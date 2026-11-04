package com.example.example.service.impl;

import com.example.example.dto.LotDto;
import com.example.example.dto.MarketOverview;
import com.example.example.dto.SellRequest;
import com.example.example.entity.Market;
import com.example.example.entity.Product;
import com.example.example.entity.UserSession;
import com.example.example.entity.Wallet;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.MarketRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.UserSessionRepository;
import com.example.example.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MarketServiceImpl {

    private static final int MAX_ACTIVE_LOTS = 10;
    private static final int MAX_QTY = 100;
    private static final int MAX_PRICE = 1_000_000;

    private final MarketRepository marketRepository;
    private final WalletRepository walletRepository;
    private final UserSessionRepository userSessionRepository;
    private final ProductRepository productRepository;
    private final JsonMapper jsonMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // ---------- чтение ----------

    /** Не readOnly: кошелёк игрока создаётся при первом обращении. */
    @Transactional
    public MarketOverview overview(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        findUserSession(code, userId);
        return buildOverview(code, userId);
    }

    // ---------- выставить лот ----------

    @Transactional
    public MarketOverview sell(String sessionCode, Long userId, SellRequest req) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);

        if (req == null || req.productCode() == null) throw new CommonException("Product not found");
        if (req.quantity() < 1 || req.quantity() > MAX_QTY) throw new CommonException("Invalid quantity");
        if (req.price() < 1 || req.price() > MAX_PRICE) throw new CommonException("Invalid price");
        String desc = req.description() == null ? null : req.description().trim();
        if (desc != null && desc.length() > 100) desc = desc.substring(0, 100);
        if (desc != null && desc.isEmpty()) desc = null;

        Product product = productRepository.findByCode(req.productCode())
                .orElseThrow(() -> new CommonException("Product not found"));

        long active = marketRepository.countBySessionCodeAndSellerIdAndStatus(code, userId, Market.Status.ACTIVE);
        if (active >= MAX_ACTIVE_LOTS) throw new CommonException("Too many active lots");

        // товар уходит со склада в лот
        Storage storage = storageCopy(us);
        if (!storage.remove(product.getCode(), req.quantity())) {
            throw new CommonException("Not enough items in storage");
        }
        saveStorage(us, storage);

        Market lot = new Market();
        lot.setUserSession(us);
        lot.setSessionCode(code);
        lot.setSellerId(userId);
        lot.setSellerNick(nickOf(us));
        lot.setProduct(product);
        lot.setQuantity(req.quantity());
        lot.setPrice(req.price());
        lot.setDescription(desc);
        lot.setStatus(Market.Status.ACTIVE);
        lot.setCreatedAt(Instant.now());
        marketRepository.save(lot);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    // ---------- купить ----------

    @Transactional
    public MarketOverview buy(String sessionCode, Long userId, Long slotId) {
        String code = sessionCode.toUpperCase();
        UserSession buyer = findUserSession(code, userId);

        Market lot = marketRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new CommonException("Lot is no longer available"));
        if (lot.getStatus() != Market.Status.ACTIVE || !code.equals(lot.getSessionCode())) {
            throw new CommonException("Lot is no longer available");
        }
        Long sellerId = lot.getSellerId();
        if (Objects.equals(sellerId, userId)) {
            throw new CommonException("Cannot buy own lot");
        }

        long total = (long) lot.getPrice() * lot.getQuantity();
        String productCode = lot.getProduct().getCode();

        // кошельки блокируем в одном и том же порядке (по id игрока), чтобы не поймать deadlock
        Wallet buyerWallet;
        Wallet sellerWallet;
        if (userId.compareTo(sellerId) < 0) {
            buyerWallet = wallet(code, userId);
            sellerWallet = wallet(code, sellerId);
        } else {
            sellerWallet = wallet(code, sellerId);
            buyerWallet = wallet(code, userId);
        }

        if (buyerWallet.getCoins() < total) throw new CommonException("Not enough coins");

        Storage buyerStorage = storageCopy(buyer);
        if (!buyerStorage.canAdd(productCode, lot.getQuantity())) throw new CommonException("Storage full");

        // деньги: покупатель -> продавец
        buyerWallet.setCoins(buyerWallet.getCoins() - total);
        sellerWallet.setCoins(sellerWallet.getCoins() + total);
        walletRepository.save(buyerWallet);
        walletRepository.save(sellerWallet);

        // товар: в склад покупателя (у продавца он уже списан при выставлении лота)
        buyerStorage.add(productCode, lot.getQuantity());
        saveStorage(buyer, buyerStorage);

        lot.setStatus(Market.Status.SOLD);
        lot.setClosedAt(Instant.now());
        marketRepository.save(lot);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    // ---------- снять лот ----------

    @Transactional
    public MarketOverview cancel(String sessionCode, Long userId, Long slotId) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);

        Market lot = marketRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new CommonException("Lot is no longer available"));
        if (lot.getStatus() != Market.Status.ACTIVE || !code.equals(lot.getSessionCode())) {
            throw new CommonException("Lot is no longer available");
        }
        if (!Objects.equals(lot.getSellerId(), userId)) {
            throw new CommonException("Only the seller can cancel");
        }

        // товар возвращается на склад
        Storage storage = storageCopy(us);
        String productCode = lot.getProduct().getCode();
        if (!storage.canAdd(productCode, lot.getQuantity())) throw new CommonException("Storage full");
        storage.add(productCode, lot.getQuantity());
        saveStorage(us, storage);

        lot.setStatus(Market.Status.CANCELLED);
        lot.setClosedAt(Instant.now());
        marketRepository.save(lot);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    // ---------- внутреннее ----------

    private UserSession findUserSession(String sessionCode, Long userId) {
        return userSessionRepository.findBySessionCodeAndUserId(sessionCode, userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));
    }

    /** Кошелёк игрока (с блокировкой); создаётся со стартовыми монетами при первом обращении. */
    private Wallet wallet(String sessionCode, Long userId) {
        return walletRepository.findForUpdate(sessionCode, userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setSessionCode(sessionCode);
            w.setUserId(userId);
            return walletRepository.save(w);
        });
    }

    private MarketOverview buildOverview(String code, Long userId) {
        List<LotDto> lots = marketRepository
                .findBySessionCodeAndStatusOrderByCreatedAtDesc(code, Market.Status.ACTIVE)
                .stream().map(this::toDto).toList();
        return new MarketOverview(wallet(code, userId).getCoins(), lots);
    }

    private LotDto toDto(Market m) {
        Product p = m.getProduct();
        return new LotDto(m.getSlotId(), m.getSellerId(), m.getSellerNick(),
                p.getCode(), p.getTitle(), p.getKind().name(),
                m.getQuantity(), m.getPrice(), m.getDescription(), m.getCreatedAt());
    }

    /** Ник продавца. Если у User поле называется иначе — поправьте здесь. */
    private String nickOf(UserSession us) {
        String nick = us.getUser().getNickName();
        return nick == null || nick.isBlank() ? "Игрок" : nick;
    }

    /** Копия склада: так JSON-колонка гарантированно помечается изменённой. */
    private Storage storageCopy(UserSession us) {
        Storage s = us.getStorage();
        return s == null ? new Storage() : jsonMapper.convertValue(s, Storage.class);
    }

    private void saveStorage(UserSession us, Storage storage) {
        us.setStorage(storage);
        userSessionRepository.save(us);
    }

    private void publishAfterCommit(String sessionCode) {
        String destination = "/topic/market." + sessionCode;
        Map<String, String> payload = Map.of("type", "changed");

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(destination, payload);
                }
            });
        } else {
            messagingTemplate.convertAndSend(destination, payload);
        }
    }
}