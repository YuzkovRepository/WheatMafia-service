package com.example.example.service.impl;

import com.example.example.dto.DealDto;
import com.example.example.entity.BuyOrder;
import com.example.example.entity.Contract;
import com.example.example.entity.DealItem;
import com.example.example.entity.Product;
import com.example.example.entity.UserSession;
import com.example.example.entity.Wallet;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.BuyOrderRepository;
import com.example.example.repository.ContractRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Обмен между игроками:
 *  - контракты: набор товаров в обмен на другой набор (эскроу отдаваемого);
 *  - суперзаказы: «куплю N штук по X монет за штуку» (эскроу монет, выполнение частями).
 */
@Service
@RequiredArgsConstructor
public class DealServiceImpl {

    private static final int MAX_ACTIVE = 10;      // активных контрактов / заказов на игрока
    private static final int MAX_ITEMS = 5;        // видов товара в одной стороне контракта
    private static final int MAX_QTY = 100;
    private static final int MAX_PRICE = 1_000_000;

    private final ContractRepository contractRepository;
    private final BuyOrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final UserSessionRepository userSessionRepository;
    private final ProductRepository productRepository;
    private final JsonMapper jsonMapper;
    private final SimpMessagingTemplate messagingTemplate;

    // ================= чтение =================

    /** Не readOnly: кошелёк игрока создаётся при первом обращении. */
    @Transactional
    public DealDto.Overview overview(String sessionCode, Long userId) {
        String code = sessionCode.toUpperCase();
        findUserSession(code, userId);
        return buildOverview(code, userId);
    }

    // ================= контракты =================

    @Transactional
    public DealDto.Overview createContract(String sessionCode, Long userId, DealDto.CreateContract req) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);
        if (req == null) throw new CommonException("Invalid items");

        List<DealItem> offer = normalize(req.offer());
        List<DealItem> request = normalize(req.request());

        Set<String> offered = new HashSet<>();
        offer.forEach(i -> offered.add(i.getCode()));
        for (DealItem i : request) {
            if (offered.contains(i.getCode())) throw new CommonException("Offer and request must differ");
        }

        String desc = cleanDescription(req.description());

        if (contractRepository.countBySessionCodeAndCreatorIdAndStatus(code, userId, Contract.Status.ACTIVE) >= MAX_ACTIVE) {
            throw new CommonException("Too many active contracts");
        }

        // отдаваемое уходит со склада в эскроу
        Storage storage = storageCopy(us);
        for (DealItem i : offer) {
            if (!storage.remove(i.getCode(), i.getQty())) throw new CommonException("Not enough items in storage");
        }
        saveStorage(us, storage);

        Contract c = new Contract();
        c.setSessionCode(code);
        c.setCreatorId(userId);
        c.setCreatorNick(nickOf(us));
        c.setOffer(offer);
        c.setRequest(request);
        c.setDescription(desc);
        c.setStatus(Contract.Status.ACTIVE);
        c.setCreatedAt(Instant.now());
        contractRepository.save(c);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    @Transactional
    public DealDto.Overview acceptContract(String sessionCode, Long userId, Long contractId) {
        String code = sessionCode.toUpperCase();
        UserSession taker = findUserSession(code, userId);

        Contract c = lockContract(contractId, code);
        if (Objects.equals(c.getCreatorId(), userId)) throw new CommonException("Cannot accept own contract");
        UserSession creator = findUserSession(code, c.getCreatorId());

        // принимающий: отдаёт запрошенное, получает предложенное
        Storage ts = storageCopy(taker);
        for (DealItem i : c.getRequest()) {
            if (!ts.remove(i.getCode(), i.getQty())) throw new CommonException("Not enough items in storage");
        }
        for (DealItem i : c.getOffer()) {
            if (!ts.canAdd(i.getCode(), i.getQty())) throw new CommonException("Storage full");
            ts.add(i.getCode(), i.getQty());
        }

        // создатель: получает запрошенное (его предложение уже лежит в эскроу)
        Storage cs = storageCopy(creator);
        for (DealItem i : c.getRequest()) {
            if (!cs.canAdd(i.getCode(), i.getQty())) throw new CommonException("Creator storage is full");
            cs.add(i.getCode(), i.getQty());
        }

        saveStorage(taker, ts);
        saveStorage(creator, cs);

        c.setStatus(Contract.Status.DONE);
        c.setTakerId(userId);
        c.setTakerNick(nickOf(taker));
        c.setClosedAt(Instant.now());
        contractRepository.save(c);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    @Transactional
    public DealDto.Overview cancelContract(String sessionCode, Long userId, Long contractId) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);

        Contract c = lockContract(contractId, code);
        if (!Objects.equals(c.getCreatorId(), userId)) throw new CommonException("Only the creator can cancel");

        // эскроу возвращается на склад
        Storage storage = storageCopy(us);
        for (DealItem i : c.getOffer()) {
            if (!storage.canAdd(i.getCode(), i.getQty())) throw new CommonException("Storage full");
            storage.add(i.getCode(), i.getQty());
        }
        saveStorage(us, storage);

        c.setStatus(Contract.Status.CANCELLED);
        c.setClosedAt(Instant.now());
        contractRepository.save(c);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    // ================= суперзаказы =================

    @Transactional
    public DealDto.Overview createOrder(String sessionCode, Long userId, DealDto.CreateOrder req) {
        String code = sessionCode.toUpperCase();
        UserSession us = findUserSession(code, userId);

        if (req == null || req.productCode() == null) throw new CommonException("Product not found");
        if (req.quantity() < 1 || req.quantity() > MAX_QTY) throw new CommonException("Invalid quantity");
        if (req.price() < 1 || req.price() > MAX_PRICE) throw new CommonException("Invalid price");

        Product product = productRepository.findByCode(req.productCode())
                .orElseThrow(() -> new CommonException("Product not found"));

        if (orderRepository.countBySessionCodeAndBuyerIdAndStatus(code, userId, BuyOrder.Status.ACTIVE) >= MAX_ACTIVE) {
            throw new CommonException("Too many active orders");
        }

        // монеты резервируются сразу
        long total = (long) req.quantity() * req.price();
        Wallet w = wallet(code, userId);
        if (w.getCoins() < total) throw new CommonException("Not enough coins");
        w.setCoins(w.getCoins() - total);
        walletRepository.save(w);

        BuyOrder o = new BuyOrder();
        o.setSessionCode(code);
        o.setBuyerId(userId);
        o.setBuyerNick(nickOf(us));
        o.setProduct(product);
        o.setQuantity(req.quantity());
        o.setFilled(0);
        o.setPrice(req.price());
        o.setDescription(cleanDescription(req.description()));
        o.setStatus(BuyOrder.Status.ACTIVE);
        o.setCreatedAt(Instant.now());
        orderRepository.save(o);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    /** Игрок продаёт заказчику quantity штук со своего склада и получает монеты. */
    @Transactional
    public DealDto.Overview fulfillOrder(String sessionCode, Long userId, Long orderId, DealDto.FulfillOrder req) {
        String code = sessionCode.toUpperCase();
        UserSession seller = findUserSession(code, userId);

        BuyOrder o = lockOrder(orderId, code);
        if (Objects.equals(o.getBuyerId(), userId)) throw new CommonException("Cannot fulfill own order");

        int qty = req == null ? 0 : req.quantity();
        if (qty < 1 || qty > o.remaining()) throw new CommonException("Invalid quantity");

        String productCode = o.getProduct().getCode();
        UserSession buyer = findUserSession(code, o.getBuyerId());

        Storage ss = storageCopy(seller);
        if (!ss.remove(productCode, qty)) throw new CommonException("Not enough items in storage");

        Storage bs = storageCopy(buyer);
        if (!bs.canAdd(productCode, qty)) throw new CommonException("Buyer storage is full");
        bs.add(productCode, qty);

        saveStorage(seller, ss);
        saveStorage(buyer, bs);

        // деньги заказчика уже зарезервированы — выплачиваем продавцу
        Wallet sw = wallet(code, userId);
        sw.setCoins(sw.getCoins() + (long) qty * o.getPrice());
        walletRepository.save(sw);

        o.setFilled(o.getFilled() + qty);
        if (o.remaining() == 0) {
            o.setStatus(BuyOrder.Status.DONE);
            o.setClosedAt(Instant.now());
        }
        orderRepository.save(o);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    @Transactional
    public DealDto.Overview cancelOrder(String sessionCode, Long userId, Long orderId) {
        String code = sessionCode.toUpperCase();
        findUserSession(code, userId);

        BuyOrder o = lockOrder(orderId, code);
        if (!Objects.equals(o.getBuyerId(), userId)) throw new CommonException("Only the buyer can cancel");

        // возвращаем резерв за невыполненный остаток
        Wallet w = wallet(code, userId);
        w.setCoins(w.getCoins() + (long) o.remaining() * o.getPrice());
        walletRepository.save(w);

        o.setStatus(BuyOrder.Status.CANCELLED);
        o.setClosedAt(Instant.now());
        orderRepository.save(o);

        publishAfterCommit(code);
        return buildOverview(code, userId);
    }

    // ================= внутреннее =================

    private Contract lockContract(Long id, String code) {
        Contract c = contractRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CommonException("Contract is no longer available"));
        if (c.getStatus() != Contract.Status.ACTIVE || !code.equals(c.getSessionCode())) {
            throw new CommonException("Contract is no longer available");
        }
        return c;
    }

    private BuyOrder lockOrder(Long id, String code) {
        BuyOrder o = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CommonException("Order is no longer available"));
        if (o.getStatus() != BuyOrder.Status.ACTIVE || !code.equals(o.getSessionCode())) {
            throw new CommonException("Order is no longer available");
        }
        return o;
    }

    /** Проверка стороны контракта: 1..5 разных существующих продуктов, количество 1..100. */
    private List<DealItem> normalize(List<DealDto.Item> raw) {
        if (raw == null || raw.isEmpty()) throw new CommonException("Invalid items");
        if (raw.size() > MAX_ITEMS) throw new CommonException("Too many item types");

        List<DealItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DealDto.Item i : raw) {
            if (i == null || i.code() == null) throw new CommonException("Product not found");
            if (i.qty() < 1 || i.qty() > MAX_QTY) throw new CommonException("Invalid quantity");
            if (!seen.add(i.code())) throw new CommonException("Duplicate product");
            productRepository.findByCode(i.code()).orElseThrow(() -> new CommonException("Product not found"));
            out.add(new DealItem(i.code(), i.qty()));
        }
        return out;
    }

    private String cleanDescription(String d) {
        if (d == null) return null;
        String s = d.trim();
        if (s.length() > 100) s = s.substring(0, 100);
        return s.isEmpty() ? null : s;
    }

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

    private DealDto.Overview buildOverview(String code, Long userId) {
        List<DealDto.ContractView> contracts = contractRepository
                .findBySessionCodeAndStatusOrderByCreatedAtDesc(code, Contract.Status.ACTIVE)
                .stream().map(this::toView).toList();
        List<DealDto.OrderView> orders = orderRepository
                .findBySessionCodeAndStatusOrderByCreatedAtDesc(code, BuyOrder.Status.ACTIVE)
                .stream().map(this::toView).toList();
        return new DealDto.Overview(wallet(code, userId).getCoins(), contracts, orders);
    }

    private DealDto.ContractView toView(Contract c) {
        return new DealDto.ContractView(c.getId(), c.getCreatorId(), c.getCreatorNick(),
                c.getOffer().stream().map(i -> new DealDto.Item(i.getCode(), i.getQty())).toList(),
                c.getRequest().stream().map(i -> new DealDto.Item(i.getCode(), i.getQty())).toList(),
                c.getDescription(), c.getCreatedAt());
    }

    private DealDto.OrderView toView(BuyOrder o) {
        Product p = o.getProduct();
        return new DealDto.OrderView(o.getId(), o.getBuyerId(), o.getBuyerNick(),
                p.getCode(), p.getTitle(), o.getQuantity(), o.remaining(), o.getPrice(),
                o.getDescription(), o.getCreatedAt());
    }

    /** Ник игрока. Если у User поле называется иначе — поправьте здесь. */
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
        String destination = "/topic/deals." + sessionCode;
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