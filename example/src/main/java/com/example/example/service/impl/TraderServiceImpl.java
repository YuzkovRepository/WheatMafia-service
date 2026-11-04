package com.example.example.service.impl;

import com.example.example.dto.TraderDto;
import com.example.example.entity.Product;
import com.example.example.entity.UserSession;
import com.example.example.entity.Wallet;
import com.example.example.entity.board.Storage;
import com.example.example.exception.CommonException;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.UserSessionRepository;
import com.example.example.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Скупщик: покупает продукты со склада игрока по фиксированным ценам за монеты.
 * Нелегальные товары (ILLEGAL) не берёт — их можно продать только другим игрокам на маркетплейсе.
 */
@Service
@RequiredArgsConstructor
public class TraderServiceImpl {

    private static final int MAX_QTY = 1000;

    /**
     * Цены скупки за 1 шт. Подобраны так, чтобы переработка была выгоднее продажи сырья
     * (например, 4 винограда = 48, а вино = 80). Продукта нет в таблице — score * 3.
     */
    private static final Map<String, Integer> BUY_PRICES = Map.ofEntries(
            // культуры
            Map.entry("wheat", 3),
            Map.entry("tomato", 6),
            Map.entry("potato", 6),
            Map.entry("sunflower", 9),
            Map.entry("grape", 12),
            // материалы
            Map.entry("flour", 12),
            Map.entry("egg", 14),
            Map.entry("milk", 16),
            Map.entry("oil", 36),
            Map.entry("tomato_can", 24),
            Map.entry("potato_sack", 24),
            // товары
            Map.entry("bread", 20),
            Map.entry("pizza", 45),
            Map.entry("wine", 80),
            Map.entry("lunch", 130)
    );

    private final WalletRepository walletRepository;
    private final UserSessionRepository userSessionRepository;
    private final ProductRepository productRepository;
    private final JsonMapper jsonMapper;

    private boolean buys(Product p) {
        return p.getLegality() != Product.Legality.ILLEGAL;
    }

    private int buyPrice(Product p) {
        Integer v = BUY_PRICES.get(p.getCode());
        return v != null ? v : Math.max(1, p.getScore() * 3);
    }

    @Transactional(readOnly = true)
    public List<TraderDto.Price> prices() {
        return productRepository.findAll().stream()
                .filter(this::buys)
                .map(p -> new TraderDto.Price(p.getCode(), buyPrice(p)))
                .toList();
    }

    @Transactional
    public TraderDto.SellResult sell(String sessionCode, Long userId, TraderDto.SellRequest req) {
        String code = sessionCode.toUpperCase();
        UserSession us = userSessionRepository.findBySessionCodeAndUserId(code, userId)
                .orElseThrow(() -> new CommonException("UserSession not found"));

        if (req == null || req.productCode() == null) throw new CommonException("Product not found");
        if (req.quantity() < 1 || req.quantity() > MAX_QTY) throw new CommonException("Invalid quantity");

        Product product = productRepository.findByCode(req.productCode())
                .orElseThrow(() -> new CommonException("Product not found"));
        if (!buys(product)) throw new CommonException("Trader does not buy this");

        // товар уходит со склада (копия — чтобы JSON-колонка точно пометилась изменённой)
        Storage storage = us.getStorage() == null
                ? new Storage()
                : jsonMapper.convertValue(us.getStorage(), Storage.class);
        if (!storage.remove(product.getCode(), req.quantity())) {
            throw new CommonException("Not enough items in storage");
        }
        us.setStorage(storage);
        userSessionRepository.save(us);

        // монеты на кошелёк
        long earned = (long) buyPrice(product) * req.quantity();
        Wallet wallet = walletRepository.findForUpdate(code, userId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setSessionCode(code);
            w.setUserId(userId);
            return walletRepository.save(w);
        });
        wallet.setCoins(wallet.getCoins() + earned);
        walletRepository.save(wallet);

        return new TraderDto.SellResult(wallet.getCoins(), earned);
    }
}