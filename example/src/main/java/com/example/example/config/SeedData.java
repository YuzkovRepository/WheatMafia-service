package com.example.example.config;

import com.example.example.entity.Factory;
import com.example.example.entity.Product;
import com.example.example.entity.ProductRecipe;
import com.example.example.entity.Research;
import com.example.example.repository.FactoryRepository;
import com.example.example.repository.ProductRecipeRepository;
import com.example.example.repository.ProductRepository;
import com.example.example.repository.ResearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SeedData implements CommandLineRunner {

    private static final boolean PRUNE_RECIPES = true;
    private static final boolean PRUNE_CATALOG = true;
    private static final boolean PRUNE_RESEARCH = true;

    private static final String ITALY = "ITALY";
    private static final String JAPAN = "JAPAN";
    private static final String EGYPT = "EGYPT";
    private static final String BRAZIL = "BRAZIL";

    private static final Product.ProductKind MAT = Product.ProductKind.MATERIAL;
    private static final Product.ProductKind GOOD = Product.ProductKind.GOODS;

    private final ProductRepository productRepo;
    private final ProductRecipeRepository recipeRepo;
    private final FactoryRepository factoryRepo;
    private final ResearchRepository researchRepo;

    private final Map<String, Factory> factories = new HashMap<>();
    private final Map<String, Product> products = new HashMap<>();
    private final Map<String, ProductRecipe> recipes = new HashMap<>();
    private final Map<String, Research> researches = new HashMap<>();
    private final Set<String> seededFactoryCodes = new HashSet<>();
    private final Set<String> seededProductCodes = new HashSet<>();
    private final Set<String> seededRecipeKeys = new HashSet<>();
    private final Set<String> seededResearchKeys = new HashSet<>();
    /** Порядок регистрации исследований (явные идут раньше автоматических). */
    private final List<String> researchOrder = new ArrayList<>();

    /** Финальные здания регионов -> исследование этапа 5, ради которого они нужны. */
    private static final Map<String, String> FINAL_BUILDING_RESEARCH = Map.of(
            "gourmet_house", "italy_italian_feast",
            "trade_house", "japan_ramen_feast",
            "bazaar", "egypt_eastern_feast",
            "port", "brazil_tropical_festival");

    /** factoryCode -> код исследования, которое первым (по этапу) открывает это здание */
    private final Map<String, String> unlockResearchByFactory = new HashMap<>();
    private final Map<String, Integer> unlockStageByFactory = new HashMap<>();

    @Override
    public void run(String... args) {
        factories.clear();
        products.clear();
        recipes.clear();
        researches.clear();
        seededFactoryCodes.clear();
        seededProductCodes.clear();
        seededRecipeKeys.clear();
        seededResearchKeys.clear();
        researchOrder.clear();
        unlockResearchByFactory.clear();
        unlockStageByFactory.clear();

        for (Factory f : factoryRepo.findAll()) factories.put(f.getCode(), f);
        for (Product p : productRepo.findAll()) products.put(p.getCode(), p);
        for (ProductRecipe r : recipeRepo.findAll()) {
            recipes.put(recipeKey(r.getFactoryCode(), r.getOutput().getCode()), r);
        }
        for (Research r : researchRepo.findAll()) researches.put(researchKey(r.getRegion(), r.getCode()), r);

        building("Склад", "warehouse", null, 3, 2);
        building("Торговец", "trader", null, 2, 2);

        seedItaly();
        seedJapan();
        seedEgypt();
        seedBrazil();

        // для каждого рецепта без явного исследования создаём его автоматически
        autoResearch();

        linkBuildingsToResearch();
        validateResearchTree();

        if (PRUNE_RECIPES) pruneRecipes();
        if (PRUNE_CATALOG) pruneCatalog();
        if (PRUNE_RESEARCH) pruneResearch();

        log.info("SeedData synced: {} factories, {} products, {} recipes, {} researches",
                seededFactoryCodes.size(), seededProductCodes.size(),
                seededRecipeKeys.size(), seededResearchKeys.size());
    }

    // =====================================================================
    // Правила дерева исследований
    //  - этап 1: культуры (бесплатно)
    //  - этап N (N >= 2) требует хотя бы одно исследование этапа N-1
    //  - здание открывает исследование ИНГРЕДИЕНТА его самого раннего продукта
    //    (пекарня — «Кукурузная мука», а не «Кукурузный хлеб»), чтобы не было
    //    замкнутого круга «продукт нужен для здания, здание нужно для продукта»
    //  - финальное здание региона открывает ингредиент блюда этапа 5
    // =====================================================================

    // =====================================================================
    // ##### ИТАЛИЯ #####
    // =====================================================================
    private void seedItaly() {
        building("Мельница", "mill", ITALY, 3, 2);
        building("Пекарня", "bakery", ITALY, 3, 2);
        building("Консервный завод", "cannery", ITALY, 3, 2);
        building("Маслобойня", "oil_press", ITALY, 3, 2);
        building("Винодельня", "winery", ITALY, 3, 3);
        building("Паста-хаус", "pasta_house", ITALY, 3, 2);
        building("Кондитерская", "patisserie", ITALY, 3, 2);
        building("Подпольная винокурня", "grappa_still", ITALY, 2, 2);
        building("Дом гастрономии", "gourmet_house", ITALY, 3, 3);

        crop("wheat", "Пшеница", ITALY, 10, 1, 1);
        crop("tomato", "Помидоры", ITALY, 15, 2, 2);
        crop("olive", "Оливки", ITALY, 20, 2, 2);
        crop("grape", "Виноград", ITALY, 30, 3, 3);
        crop("basil", "Базилик", ITALY, 12, 2, 2);
        crop("lemon", "Лимоны", ITALY, 25, 3, 3);

        make("mill", "flour", "Мука", MAT, 15, 3, in("wheat", 2));
        make("mill", "semolina", "Манная крупа", MAT, 20, 4, in("wheat", 3));
        make("bakery", "bread", "Хлеб", GOOD, 20, 5, in("flour", 1));
        make("bakery", "dough", "Тесто", MAT, 20, 4, in("flour", 2));
        make("bakery", "grissini", "Гриссини", GOOD, 30, 7, in("dough", 1), in("semolina", 1));
        make("cannery", "tomato_sauce", "Томатный соус", MAT, 25, 5, in("tomato", 3));
        make("cannery", "dried_tomato", "Вяленые томаты", GOOD, 30, 6, in("tomato", 2));
        make("oil_press", "olive_oil", "Оливковое масло", MAT, 30, 6, in("olive", 3));
        make("oil_press", "pesto", "Песто", GOOD, 35, 8, in("basil", 2), in("olive_oil", 1));
        make("winery", "grape_juice", "Виноградный сок", GOOD, 25, 5, in("grape", 2));
        make("winery", "wine", "Вино", GOOD, 60, 12, in("grape", 4));
        make("winery", "balsamic", "Бальзамический уксус", MAT, 60, 10, in("wine", 2));

        make("pasta_house", "pasta", "Паста", MAT, 40, 8, in("flour", 2), in("egg", 1));
        make("pasta_house", "risotto", "Ризотто", GOOD, 50, 12, in("rice", 2), in("olive_oil", 1));
        make("pasta_house", "tomato_pizza", "Томатная пицца", GOOD, 50, 12, in("dough", 1), in("tomato_sauce", 1), in("cheese", 1));
        make("pasta_house", "caprese", "Капрезе", GOOD, 35, 9, in("tomato", 1), in("cheese", 1), in("basil", 1));
        make("bakery", "focaccia", "Фокачча", GOOD, 40, 10, in("dough", 1), in("olive_oil", 1), in("salt", 1));
        make("cannery", "pickled_olives", "Маринованные оливки", MAT, 40, 8, in("olive", 2), in("salt", 1));
        make("cannery", "lemon_jam", "Лимонный джем", MAT, 40, 8, in("lemon", 2), in("sugar", 1));
        make("winery", "limoncello", "Лимончелло", GOOD, 70, 14, in("lemon", 2), in("sugar", 1));
        make("patisserie", "lemon_sorbet", "Лимонный сорбет", GOOD, 45, 10, in("lemon", 2), in("sugar", 1));
        make("patisserie", "biscotti", "Бискотти", GOOD, 45, 10, in("flour", 1), in("egg", 1), in("semolina", 1));

        make("pasta_house", "cheese_pasta", "Сырная паста", GOOD, 60, 16, in("pasta", 1), in("cheese", 1), in("tomato", 1));
        make("pasta_house", "seafood_pasta", "Паста с морепродуктами", GOOD, 70, 20, in("pasta", 1), in("tomato_sauce", 1), in("fish", 1));
        make("pasta_house", "lasagna", "Лазанья", GOOD, 80, 22, in("pasta", 2), in("tomato_sauce", 1), in("cheese", 1), in("beef", 1));
        make("pasta_house", "arancini", "Аранчини", GOOD, 70, 18, in("risotto", 1), in("flour", 1), in("egg", 1));
        make("pasta_house", "festival_pizza", "Праздничная пицца", GOOD, 70, 20, in("dough", 1), in("tomato_sauce", 1), in("cheese", 1), in("olive_oil", 1));
        make("pasta_house", "seafood_pizza", "Пицца с морепродуктами", GOOD, 75, 22, in("dough", 1), in("tomato_sauce", 1), in("fish", 1), in("cheese", 1));
        make("patisserie", "coffee_tiramisu", "Кофейный тирамису", GOOD, 80, 24, in("flour", 1), in("egg", 1), in("coffee", 1), in("sugar", 1));
        make("patisserie", "chocolate_tart", "Шоколадный тарт", GOOD, 75, 22, in("dough", 1), in("chocolate", 1), in("egg", 1));
        make("patisserie", "panna_cotta", "Панна-котта", GOOD, 65, 18, in("milk", 1), in("sugar", 1), in("lemon", 1));
        make("patisserie", "gelato", "Джелато", GOOD, 60, 16, in("milk", 1), in("sugar", 1), in("chocolate", 1));
        make("patisserie", "matcha_gelato", "Джелато с матчей", GOOD, 80, 26, in("gelato", 1), in("green_tea", 1));
        make("winery", "aperitivo", "Аперитив", GOOD, 80, 20, in("wine", 1), in("lemon", 1), in("sugar", 1));
        illegal("grappa_still", "grappa", "Граппа (самогон)", GOOD, 90, 28, in("wine", 2), in("sugar", 1));

        make("gourmet_house", "italian_feast", "Банкетный набор", GOOD, 160, 60, in("festival_pizza", 1), in("seafood_pasta", 1), in("wine", 1), in("coffee_tiramisu", 1));
        make("gourmet_house", "gourmet_basket", "Гастрономическая корзина", GOOD, 150, 55, in("wine", 1), in("cheese", 1), in("olive_oil", 1), in("coffee", 1), in("green_tea", 1));
        make("gourmet_house", "aperitivo_set", "Набор к аперитиву", GOOD, 120, 42, in("aperitivo", 1), in("pickled_olives", 1), in("focaccia", 1), in("cheese", 1));
        make("gourmet_house", "dessert_tray", "Десертное ассорти", GOOD, 130, 48, in("coffee_tiramisu", 1), in("panna_cotta", 1), in("lemon_sorbet", 1), in("matcha_gelato", 1));
        make("gourmet_house", "italy_grand_export", "Золотой экспорт Италии", GOOD, 240, 120, in("italian_feast", 1), in("sushi_platter", 1), in("gourmet_basket", 1), in("balsamic", 1));

        // ---- Дерево исследований Италии ----
        // Этап 2 (требуют культуру)
        res("italy_flour", "Мука", 2, "mill", "flour", cost("wheat", 3), List.of("italy_crop_wheat"));
        res("italy_tomato_sauce", "Томатный соус", 2, "cannery", "tomato_sauce", cost("tomato", 3), List.of("italy_crop_tomato"));
        res("italy_olive_oil", "Оливковое масло", 2, "oil_press", "olive_oil", cost("olive", 3), List.of("italy_crop_olive"));
        res("italy_grape_juice", "Виноградный сок", 2, "winery", "grape_juice", cost("grape", 2), List.of("italy_crop_grape"));
        res("italy_wine", "Вино", 2, "winery", "wine", cost("grape", 3), List.of("italy_crop_grape"));

        // Этап 3 (требуют этап 2)
        res("italy_bread", "Хлеб", 3, "bakery", "bread", cost("flour", 2), List.of("italy_flour"));
        res("italy_dough", "Тесто", 3, "bakery", "dough", cost("flour", 2), List.of("italy_flour"));
        res("italy_pasta", "Паста", 3, "pasta_house", "pasta", cost("flour", 2), List.of("italy_flour"));
        res("italy_risotto", "Ризотто", 3, "pasta_house", "risotto", cost("olive_oil", 2), List.of("italy_olive_oil"));
        res("italy_caprese", "Капрезе", 3, "pasta_house", "caprese", cost("tomato", 2), List.of("italy_tomato_sauce", "italy_olive_oil"));
        res("italy_lemon_jam", "Лимонный джем", 3, "cannery", "lemon_jam", cost("lemon", 2), List.of("italy_tomato_sauce"));
        res("italy_biscotti", "Бискотти", 3, "patisserie", "biscotti", cost("flour", 2), List.of("italy_flour"));
        res("italy_aperitivo", "Аперитив", 3, "winery", "aperitivo", cost("wine", 2), List.of("italy_wine"));

        // Этап 4 (требуют этап 3)
        res("italy_focaccia", "Фокачча", 4, "bakery", "focaccia", cost("dough", 2), List.of("italy_dough"));
        res("italy_tomato_pizza", "Томатная пицца", 4, "pasta_house", "tomato_pizza", cost("dough", 2), List.of("italy_dough"));
        res("italy_lasagna", "Лазанья", 4, "pasta_house", "lasagna", cost("pasta", 2), List.of("italy_pasta"));
        res("italy_seafood_pasta", "Паста с морепродуктами", 4, "pasta_house", "seafood_pasta", cost("pasta", 2), List.of("italy_pasta"));
        res("italy_coffee_tiramisu", "Кофейный тирамису", 4, "patisserie", "coffee_tiramisu", cost("biscotti", 2), List.of("italy_biscotti"));
        res("italy_grappa", "Граппа (самогон)", 4, "grappa_still", "grappa", cost("wine", 2), List.of("italy_aperitivo"));

        // Этап 5 (требуют этап 4)
        res("italy_festival_pizza", "Праздничная пицца", 5, "pasta_house", "festival_pizza", cost("tomato_pizza", 2), List.of("italy_tomato_pizza"));
        res("italy_italian_feast", "Банкетный набор", 5, "gourmet_house", "italian_feast", cost("lasagna", 2), List.of("italy_lasagna", "italy_coffee_tiramisu"));
    }

    // =====================================================================
    // ##### ЯПОНИЯ #####
    // =====================================================================
    private void seedJapan() {
        building("Мельница и пресс", "rice_mill", JAPAN, 3, 2);
        building("Ферментационный цех", "brewery", JAPAN, 3, 3);
        building("Мастерская тофу", "tofu_house", JAPAN, 3, 2);
        building("Чайный дом", "tea_house", JAPAN, 3, 2);
        building("Сушильня", "drying_house", JAPAN, 3, 2);
        building("Рыбацкая хижина", "fishery", JAPAN, 3, 2);
        building("Суши-бар", "sushi_bar", JAPAN, 3, 2);
        building("Японская кухня", "japanese_kitchen", JAPAN, 3, 3);
        building("Лавка вагаси", "wagashi_shop", JAPAN, 3, 2);
        building("Подпольная винокурня саке", "sake_still", JAPAN, 2, 2);
        building("Торговый дом", "trade_house", JAPAN, 3, 3);

        crop("rice", "Рис", JAPAN, 20, 2, 2);
        crop("soybean", "Соя", JAPAN, 20, 2, 2);
        crop("tea_leaf", "Чайный лист", JAPAN, 25, 2, 2);
        crop("seaweed", "Морские водоросли", JAPAN, 15, 1, 1);
        crop("ginger", "Имбирь", JAPAN, 18, 2, 2);
        crop("sesame", "Кунжут", JAPAN, 18, 2, 2);

        make("rice_mill", "rice_flour", "Рисовая мука", MAT, 20, 3, in("rice", 2));
        make("rice_mill", "sesame_oil", "Кунжутное масло", MAT, 30, 6, in("sesame", 3));
        make("brewery", "rice_vinegar", "Рисовый уксус", MAT, 30, 5, in("rice", 2));
        make("brewery", "sake", "Саке", GOOD, 70, 14, in("rice", 3), in("rice_flour", 1));
        make("brewery", "pickled_ginger", "Маринованный имбирь", MAT, 30, 6, in("ginger", 2), in("rice_vinegar", 1));
        make("tofu_house", "tofu", "Тофу", MAT, 25, 5, in("soybean", 2));
        make("tofu_house", "soy_milk", "Соевое молоко", GOOD, 25, 5, in("soybean", 2));
        make("tofu_house", "fried_tofu", "Жареный тофу", GOOD, 35, 9, in("tofu", 1), in("sesame_oil", 1));
        make("tea_house", "green_tea", "Зелёный чай", MAT, 30, 6, in("tea_leaf", 2));
        make("tea_house", "matcha", "Матча", MAT, 45, 9, in("tea_leaf", 3));
        make("tea_house", "ginger_tea", "Имбирный чай", GOOD, 35, 9, in("green_tea", 1), in("ginger", 1));
        make("drying_house", "dried_seaweed", "Сушёные водоросли", MAT, 25, 5, in("seaweed", 2));
        make("fishery", "fish", "Рыба", MAT, 20, 3);
        make("sushi_bar", "onigiri", "Онигири", GOOD, 35, 8, in("rice", 2), in("fish", 1));
        make("sushi_bar", "maki", "Маки-ролл", GOOD, 40, 10, in("rice", 1), in("dried_seaweed", 1), in("fish", 1));
        make("sushi_bar", "sushi", "Суши", GOOD, 50, 14, in("rice", 2), in("fish", 1), in("rice_vinegar", 1), in("dried_seaweed", 1));

        make("brewery", "soy_sauce", "Соевый соус", MAT, 45, 9, in("soybean", 2), in("salt", 1));
        make("brewery", "miso_paste", "Мисо-паста", MAT, 50, 10, in("soybean", 2), in("rice", 1), in("salt", 1));
        make("drying_house", "dried_fish", "Вяленая рыба", MAT, 40, 8, in("fish", 2), in("salt", 1));
        make("japanese_kitchen", "miso_soup", "Суп мисо", GOOD, 45, 12, in("tofu", 1), in("dried_seaweed", 1), in("miso_paste", 1));
        make("japanese_kitchen", "fried_rice", "Жареный рис", GOOD, 50, 13, in("rice", 2), in("egg", 1), in("sesame_oil", 1));
        make("japanese_kitchen", "noodles", "Лапша", MAT, 40, 9, in("flour", 2), in("rice_flour", 1));
        make("japanese_kitchen", "teriyaki_fish", "Рыба терияки", GOOD, 55, 14, in("fish", 1), in("soy_sauce", 1), in("ginger", 1));
        make("wagashi_shop", "mochi", "Моти", GOOD, 45, 12, in("rice_flour", 2), in("sugar", 1));
        make("wagashi_shop", "matcha_dessert", "Матча-десерт", GOOD, 55, 15, in("rice_flour", 1), in("green_tea", 1), in("sugar", 1));

        make("japanese_kitchen", "ramen", "Рамен", GOOD, 75, 22, in("noodles", 1), in("miso_paste", 1), in("egg", 1), in("dried_seaweed", 1));
        make("japanese_kitchen", "gyudon", "Гюдон", GOOD, 70, 22, in("rice", 2), in("beef", 1), in("soy_sauce", 1));
        make("japanese_kitchen", "tempura", "Темпура", GOOD, 65, 20, in("fish", 1), in("flour", 1), in("egg", 1));
        make("japanese_kitchen", "curry_rice", "Рис с карри", GOOD, 70, 22, in("rice", 2), in("spice", 1), in("beef", 1));
        make("japanese_kitchen", "festival_soup", "Праздничный суп", GOOD, 60, 18, in("tofu", 1), in("dried_seaweed", 1), in("tomato_sauce", 1));
        make("sushi_bar", "california_roll", "Калифорния-ролл", GOOD, 60, 18, in("maki", 1), in("mango", 1), in("sesame", 1));
        make("sushi_bar", "tempura_roll", "Ролл с темпурой", GOOD, 70, 22, in("maki", 1), in("tempura", 1));
        make("wagashi_shop", "chocolate_mochi", "Шоколадные моти", GOOD, 60, 18, in("mochi", 1), in("chocolate", 1));
        make("wagashi_shop", "dorayaki", "Доряки", GOOD, 60, 18, in("flour", 1), in("egg", 1), in("sugar", 1));
        make("wagashi_shop", "matcha_latte", "Матча-латте", GOOD, 50, 16, in("matcha", 1), in("milk", 1), in("sugar", 1));
        illegal("sake_still", "bootleg_sake", "Самопальное саке", GOOD, 90, 28, in("sake", 2), in("sugar", 1));

        make("trade_house", "sushi_platter", "Большой набор суши", GOOD, 150, 52, in("sushi", 2), in("california_roll", 1), in("tempura_roll", 1), in("olive_oil", 1));
        make("trade_house", "tea_ceremony_set", "Чайная церемония", GOOD, 140, 46, in("green_tea", 1), in("matcha_dessert", 1), in("chocolate_mochi", 1), in("linen_cloth", 1));
        make("trade_house", "ramen_feast", "Рамен-банкет", GOOD, 160, 56, in("ramen", 1), in("gyudon", 1), in("tempura", 1), in("festival_soup", 1));
        make("trade_house", "japan_export_box", "Торговый караван Японии", GOOD, 170, 60, in("soy_sauce", 1), in("sushi_platter", 1), in("coffee", 1), in("spice", 1));
        make("trade_house", "japan_grand_export", "Золотой экспорт Японии", GOOD, 240, 120, in("ramen_feast", 1), in("tea_ceremony_set", 1), in("tropical_festival", 1), in("sake", 1));

        // ---- Дерево исследований Японии ----
        // Этап 2 (требуют культуру)
        res("japan_rice_flour", "Рисовая мука", 2, "rice_mill", "rice_flour", cost("rice", 3), List.of("japan_crop_rice"));
        res("japan_rice_vinegar", "Рисовый уксус", 2, "brewery", "rice_vinegar", cost("rice", 2), List.of("japan_crop_rice"));
        res("japan_tofu", "Тофу", 2, "tofu_house", "tofu", cost("soybean", 3), List.of("japan_crop_soybean"));
        res("japan_green_tea", "Зелёный чай", 2, "tea_house", "green_tea", cost("tea_leaf", 2), List.of("japan_crop_tea_leaf"));
        res("japan_matcha", "Матча", 2, "tea_house", "matcha", cost("tea_leaf", 3), List.of("japan_crop_tea_leaf"));
        res("japan_dried_seaweed", "Сушёные водоросли", 2, "drying_house", "dried_seaweed", cost("seaweed", 2), List.of("japan_crop_seaweed"));
        res("japan_fish", "Рыба", 2, "fishery", "fish", cost("seaweed", 2), List.of("japan_crop_seaweed"));

        // Этап 3 (требуют этап 2)
        res("japan_onigiri", "Онигири", 3, "sushi_bar", "onigiri", cost("fish", 2), List.of("japan_fish"));
        res("japan_maki", "Маки-ролл", 3, "sushi_bar", "maki", cost("fish", 2), List.of("japan_fish", "japan_dried_seaweed"));
        res("japan_noodles", "Лапша", 3, "japanese_kitchen", "noodles", cost("rice_flour", 2), List.of("japan_rice_flour"));
        res("japan_fried_rice", "Жареный рис", 3, "japanese_kitchen", "fried_rice", cost("rice", 2), List.of("japan_rice_vinegar"));
        res("japan_mochi", "Моти", 3, "wagashi_shop", "mochi", cost("rice_flour", 2), List.of("japan_rice_flour"));
        res("japan_matcha_dessert", "Матча-десерт", 3, "wagashi_shop", "matcha_dessert", cost("matcha", 2), List.of("japan_matcha"));
        res("japan_soy_sauce", "Соевый соус", 3, "brewery", "soy_sauce", cost("soybean", 3), List.of("japan_tofu"));
        res("japan_miso_paste", "Мисо-паста", 3, "brewery", "miso_paste", cost("soybean", 2), List.of("japan_tofu", "japan_rice_vinegar"));
        res("japan_sake", "Саке", 3, "brewery", "sake", cost("rice", 3), List.of("japan_rice_vinegar"));

        // Этап 4 (требуют этап 3)
        res("japan_sushi", "Суши", 4, "sushi_bar", "sushi", cost("maki", 2), List.of("japan_maki"));
        res("japan_california_roll", "Калифорния-ролл", 4, "sushi_bar", "california_roll", cost("maki", 2), List.of("japan_maki"));
        res("japan_miso_soup", "Суп мисо", 4, "japanese_kitchen", "miso_soup", cost("tofu", 2), List.of("japan_miso_paste"));
        res("japan_ramen", "Рамен", 4, "japanese_kitchen", "ramen", cost("noodles", 2), List.of("japan_noodles", "japan_miso_paste"));
        res("japan_gyudon", "Гюдон", 4, "japanese_kitchen", "gyudon", cost("rice", 2), List.of("japan_soy_sauce"));
        res("japan_tempura", "Темпура", 4, "japanese_kitchen", "tempura", cost("fish", 2), List.of("japan_noodles"));
        res("japan_chocolate_mochi", "Шоколадные моти", 4, "wagashi_shop", "chocolate_mochi", cost("mochi", 2), List.of("japan_mochi"));
        res("japan_matcha_latte", "Матча-латте", 4, "wagashi_shop", "matcha_latte", cost("matcha", 2), List.of("japan_matcha_dessert"));
        res("japan_bootleg_sake", "Самопальное саке", 4, "sake_still", "bootleg_sake", cost("sake", 2), List.of("japan_sake"));

        // Этап 5 (требуют этап 4)
        res("japan_ramen_feast", "Рамен-банкет", 5, "trade_house", "ramen_feast", cost("ramen", 2), List.of("japan_ramen", "japan_gyudon"));
    }

    // =====================================================================
    // ##### ЕГИПЕТ #####
    // =====================================================================
    private void seedEgypt() {
        building("Курятник", "chicken_coop", EGYPT, 3, 3);
        building("Коровник", "cowshed", EGYPT, 3, 3);
        building("Соляная копь", "salt_mine", EGYPT, 3, 2);
        building("Мельница и пресс", "barley_mill", EGYPT, 3, 2);
        building("Сыроварня", "dairy", EGYPT, 3, 2);
        building("Мастерская", "workshop", EGYPT, 3, 2);
        building("Кухня Нила", "nile_kitchen", EGYPT, 3, 3);
        building("Печь лепёшек", "flat_oven", EGYPT, 3, 2);
        building("Лавка сладостей", "sweet_shop", EGYPT, 3, 2);
        building("Подпольная винокурня арака", "arak_still", EGYPT, 2, 2);
        building("Базар", "bazaar", EGYPT, 3, 3);

        crop("barley", "Ячмень", EGYPT, 12, 1, 1);
        crop("dates", "Финики", EGYPT, 25, 3, 3);
        crop("flax", "Лён", EGYPT, 18, 2, 2);
        crop("chickpea", "Нут", EGYPT, 18, 2, 2);
        crop("spice", "Специи", EGYPT, 20, 3, 3);
        crop("papyrus", "Папирус", EGYPT, 22, 2, 2);

        make("chicken_coop", "egg", "Яйца", MAT, 25, 3, in("barley", 2));
        make("cowshed", "milk", "Молоко", MAT, 25, 3, in("barley", 2));
        make("salt_mine", "salt", "Соль", MAT, 15, 2);
        make("barley_mill", "barley_flour", "Ячменная мука", MAT, 20, 3, in("barley", 2));
        make("barley_mill", "date_syrup", "Финиковый сироп", MAT, 35, 7, in("dates", 3));
        make("barley_mill", "flax_oil", "Льняное масло", MAT, 30, 6, in("flax", 3));
        make("dairy", "cheese", "Сыр", MAT, 45, 8, in("milk", 2));
        make("dairy", "butter", "Сливочное масло", MAT, 35, 7, in("milk", 2));
        make("dairy", "yogurt", "Йогурт", GOOD, 35, 9, in("milk", 1), in("date_syrup", 1));
        make("workshop", "linen_cloth", "Льняная ткань", MAT, 40, 8, in("flax", 2));
        make("workshop", "papyrus_paper", "Папирусная бумага", MAT, 40, 8, in("papyrus", 2));
        make("nile_kitchen", "spice_mix", "Смесь специй", MAT, 30, 6, in("spice", 2), in("salt", 1));
        make("nile_kitchen", "falafel", "Фалафель", GOOD, 40, 10, in("chickpea", 2), in("spice", 1));
        make("flat_oven", "flatbread", "Лепёшка", GOOD, 30, 8, in("barley_flour", 1), in("flax_oil", 1));
        make("sweet_shop", "date_cake", "Финиковый кекс", GOOD, 45, 12, in("barley_flour", 1), in("dates", 2), in("egg", 1));

        make("nile_kitchen", "hummus", "Хумус", GOOD, 40, 11, in("chickpea", 2), in("olive_oil", 1));
        make("nile_kitchen", "vegetable_mezze", "Овощная закуска", GOOD, 50, 14, in("hummus", 1), in("tomato_sauce", 1), in("bread", 1));
        make("nile_kitchen", "shakshuka", "Шакшука", GOOD, 50, 14, in("tomato_sauce", 1), in("egg", 2), in("spice_mix", 1));
        make("flat_oven", "cheese_flatbread", "Сырные лепёшки", GOOD, 50, 14, in("flatbread", 1), in("cheese", 1), in("tomato", 1));
        make("flat_oven", "olive_flatbread", "Лепёшка с оливковым маслом", GOOD, 45, 12, in("flatbread", 1), in("olive_oil", 1));
        make("sweet_shop", "date_pie", "Финиковый пирог", GOOD, 60, 16, in("flour", 1), in("dates", 2), in("egg", 1));
        make("sweet_shop", "baklava", "Пахлава", GOOD, 65, 17, in("flour", 1), in("butter", 1), in("date_syrup", 1));
        make("sweet_shop", "mango_yogurt", "Йогурт с манго", GOOD, 45, 13, in("yogurt", 1), in("mango", 1));
        illegal("arak_still", "date_arak", "Финиковый арак", GOOD, 90, 28, in("date_syrup", 2), in("sugar", 1));

        make("nile_kitchen", "spiced_rice", "Пряный рис с нутом", GOOD, 65, 20, in("rice", 2), in("chickpea", 1), in("spice", 1));
        make("nile_kitchen", "koshari", "Кошари", GOOD, 70, 22, in("rice", 1), in("pasta", 1), in("chickpea", 1), in("tomato_sauce", 1));
        make("nile_kitchen", "fish_tagine", "Рыбный тажин", GOOD, 70, 22, in("fish", 1), in("spice_mix", 1), in("tomato_sauce", 1));
        make("nile_kitchen", "date_coffee", "Кофе с финиковым сиропом", GOOD, 45, 18, in("coffee", 1), in("date_syrup", 1), in("milk", 1));
        make("sweet_shop", "festival_pie", "Праздничный пирог", GOOD, 75, 24, in("flour", 1), in("egg", 1), in("date_syrup", 1), in("cocoa_powder", 1));
        make("sweet_shop", "chocolate_dates", "Финики в шоколаде", GOOD, 55, 18, in("dates", 2), in("chocolate", 1));

        make("bazaar", "eastern_feast", "Восточный банкет", GOOD, 160, 58, in("hummus", 1), in("cheese_flatbread", 1), in("spiced_rice", 1), in("date_coffee", 1));
        make("bazaar", "egyptian_trade_bundle", "Торговый тюк Египта", GOOD, 150, 52, in("linen_cloth", 1), in("cheese", 1), in("date_pie", 1), in("green_tea", 1));
        make("bazaar", "oasis_banquet", "Банкет оазиса", GOOD, 160, 56, in("koshari", 1), in("fish_tagine", 1), in("vegetable_mezze", 1), in("baklava", 1));
        make("bazaar", "papyrus_gift_set", "Подарочный набор папируса", GOOD, 140, 48, in("papyrus_paper", 1), in("linen_cloth", 1), in("chocolate_dates", 1), in("green_tea", 1));
        make("bazaar", "egypt_grand_export", "Золотой экспорт Египта", GOOD, 240, 120, in("eastern_feast", 1), in("oasis_banquet", 1), in("italian_feast", 1), in("festival_pie", 1));

        // ---- Дерево исследований Египта ----
        // Этап 2 (требуют культуру)
        res("egypt_egg", "Яйца", 2, "chicken_coop", "egg", cost("barley", 3), List.of("egypt_crop_barley"));
        res("egypt_milk", "Молоко", 2, "cowshed", "milk", cost("barley", 3), List.of("egypt_crop_barley"));
        res("egypt_salt", "Соль", 2, "salt_mine", "salt", cost("barley", 2), List.of("egypt_crop_barley"));
        res("egypt_barley_flour", "Ячменная мука", 2, "barley_mill", "barley_flour", cost("barley", 3), List.of("egypt_crop_barley"));
        res("egypt_date_syrup", "Финиковый сироп", 2, "barley_mill", "date_syrup", cost("dates", 3), List.of("egypt_crop_dates"));
        res("egypt_linen_cloth", "Льняная ткань", 2, "workshop", "linen_cloth", cost("flax", 2), List.of("egypt_crop_flax"));
        res("egypt_spice_mix", "Смесь специй", 2, "nile_kitchen", "spice_mix", cost("spice", 2), List.of("egypt_crop_spice"));

        // Этап 3 (требуют этап 2)
        res("egypt_cheese", "Сыр", 3, "dairy", "cheese", cost("milk", 2), List.of("egypt_milk"));
        res("egypt_flatbread", "Лепёшка", 3, "flat_oven", "flatbread", cost("barley_flour", 2), List.of("egypt_barley_flour"));
        res("egypt_date_cake", "Финиковый кекс", 3, "sweet_shop", "date_cake", cost("barley_flour", 2), List.of("egypt_barley_flour"));
        res("egypt_baklava", "Пахлава", 3, "sweet_shop", "baklava", cost("date_syrup", 2), List.of("egypt_date_syrup"));
        res("egypt_hummus", "Хумус", 3, "nile_kitchen", "hummus", cost("chickpea", 2), List.of("egypt_spice_mix"));
        res("egypt_shakshuka", "Шакшука", 3, "nile_kitchen", "shakshuka", cost("spice_mix", 2), List.of("egypt_spice_mix", "egypt_egg"));

        // Этап 4 (требуют этап 3)
        res("egypt_cheese_flatbread", "Сырные лепёшки", 4, "flat_oven", "cheese_flatbread", cost("flatbread", 2), List.of("egypt_flatbread", "egypt_cheese"));
        res("egypt_date_pie", "Финиковый пирог", 4, "sweet_shop", "date_pie", cost("date_cake", 2), List.of("egypt_date_cake"));
        res("egypt_spiced_rice", "Пряный рис с нутом", 4, "nile_kitchen", "spiced_rice", cost("spice_mix", 2), List.of("egypt_hummus"));
        res("egypt_fish_tagine", "Рыбный тажин", 4, "nile_kitchen", "fish_tagine", cost("spice_mix", 2), List.of("egypt_hummus", "egypt_shakshuka"));
        res("egypt_date_coffee", "Кофе с финиковым сиропом", 4, "nile_kitchen", "date_coffee", cost("date_syrup", 2), List.of("egypt_baklava"));
        res("egypt_date_arak", "Финиковый арак", 4, "arak_still", "date_arak", cost("date_syrup", 2), List.of("egypt_baklava"));

        // Этап 5 (требуют этап 4)
        res("egypt_koshari", "Кошари", 5, "nile_kitchen", "koshari", cost("spiced_rice", 2), List.of("egypt_spiced_rice"));
        res("egypt_festival_pie", "Праздничный пирог", 5, "sweet_shop", "festival_pie", cost("date_pie", 2), List.of("egypt_date_pie"));
        res("egypt_eastern_feast", "Восточный банкет", 5, "bazaar", "eastern_feast", cost("hummus", 2), List.of("egypt_spiced_rice", "egypt_date_coffee"));
    }

    // =====================================================================
    // ##### БРАЗИЛИЯ #####
    // =====================================================================
    private void seedBrazil() {
        building("Обжарочная", "coffee_roastery", BRAZIL, 3, 2);
        building("Сахарный завод", "sugar_mill", BRAZIL, 3, 2);
        building("Шоколадная фабрика", "chocolate_factory", BRAZIL, 3, 2);
        building("Мукомольня", "corn_mill", BRAZIL, 3, 2);
        building("Соковыжимальня", "juice_bar", BRAZIL, 3, 2);
        building("Ранчо", "ranch", BRAZIL, 3, 3);
        building("Подпольная винокурня кашасы", "cachaca_still", BRAZIL, 2, 2);
        building("Бразильская пекарня", "br_bakery", BRAZIL, 3, 2);
        building("Кафе", "cafe", BRAZIL, 3, 2);
        building("Тропическая кухня", "br_kitchen", BRAZIL, 3, 3);
        building("Чурраскерия", "churrascaria", BRAZIL, 3, 3);
        building("Порт", "port", BRAZIL, 3, 3);

        crop("coffee_berry", "Кофейные ягоды", BRAZIL, 25, 2, 2);
        crop("sugarcane", "Сахарный тростник", BRAZIL, 20, 2, 2);
        crop("cocoa_bean", "Какао-бобы", BRAZIL, 30, 3, 3);
        crop("corn", "Кукуруза", BRAZIL, 12, 1, 1);
        crop("cassava", "Маниок", BRAZIL, 18, 2, 2);
        crop("mango", "Манго", BRAZIL, 25, 3, 3);
        crop("banana", "Бананы", BRAZIL, 20, 2, 2);

        make("coffee_roastery", "coffee", "Кофе", MAT, 40, 8, in("coffee_berry", 3));
        make("sugar_mill", "sugar", "Сахар", MAT, 30, 6, in("sugarcane", 3));
        make("sugar_mill", "molasses", "Патока", MAT, 25, 5, in("sugarcane", 2));
        make("chocolate_factory", "cocoa_powder", "Какао-порошок", MAT, 30, 7, in("cocoa_bean", 2));
        make("chocolate_factory", "chocolate", "Шоколад", MAT, 45, 10, in("cocoa_bean", 2), in("sugar", 1));
        make("corn_mill", "corn_flour", "Кукурузная мука", MAT, 20, 4, in("corn", 2));
        make("corn_mill", "cassava_flour", "Маниоковая мука", MAT, 25, 5, in("cassava", 2));
        make("corn_mill", "tapioca", "Тапиока", MAT, 25, 5, in("cassava", 2));
        make("juice_bar", "mango_juice", "Манговый сок", MAT, 30, 6, in("mango", 3));
        make("juice_bar", "banana_chips", "Банановые чипсы", GOOD, 30, 8, in("banana", 2));
        make("juice_bar", "mango_jam", "Манговый джем", GOOD, 40, 9, in("mango", 2), in("sugar", 1));
        make("ranch", "beef", "Говядина", MAT, 50, 10, in("corn", 3));
        illegal("cachaca_still", "cachaca", "Кашаса (самогон)", GOOD, 80, 24, in("sugarcane", 3), in("molasses", 1));
        make("br_bakery", "cornbread", "Кукурузный хлеб", GOOD, 30, 8, in("corn_flour", 2));
        make("br_bakery", "tapioca_crepe", "Блин из тапиоки", GOOD, 35, 10, in("tapioca", 2), in("banana", 1));
        make("cafe", "mango_sorbet", "Манговый сорбет", GOOD, 40, 11, in("mango_juice", 1), in("sugar", 1));

        make("br_bakery", "coffee_bread", "Сладкий кофейный хлеб", GOOD, 55, 14, in("flour", 1), in("coffee", 1), in("sugar", 1));
        make("br_bakery", "pao_de_queijo", "Пан-ди-кежу", GOOD, 55, 15, in("cassava_flour", 1), in("cheese", 1), in("egg", 1));
        make("br_kitchen", "tropical_porridge", "Тропическая каша", GOOD, 50, 14, in("corn_flour", 1), in("milk", 1), in("mango_juice", 1));
        make("cafe", "brigadeiro", "Бригадейро", GOOD, 45, 13, in("chocolate", 1), in("milk", 1));
        make("cafe", "coffee_latte", "Кофе с молоком", GOOD, 35, 11, in("coffee", 1), in("milk", 1));
        make("churrascaria", "churrasco", "Чураско", GOOD, 60, 16, in("beef", 1), in("salt", 1), in("corn", 1));
        make("churrascaria", "beef_stew", "Говяжье рагу", GOOD, 60, 17, in("beef", 1), in("tomato_sauce", 1), in("cassava_flour", 1));
        make("churrascaria", "soy_feijoada", "Фейжоада с соей", GOOD, 65, 18, in("beef", 1), in("soybean", 2), in("cassava_flour", 1));
        illegal("cachaca_still", "caipirinha", "Кайпиринья", GOOD, 60, 22, in("cachaca", 1), in("lemon", 1), in("sugar", 1));

        make("br_bakery", "chocolate_cake", "Шоколадный пирог", GOOD, 75, 24, in("flour", 1), in("chocolate", 1), in("egg", 1));
        make("cafe", "coffee_dessert", "Кофейный десерт", GOOD, 65, 20, in("chocolate", 1), in("coffee", 1), in("cheese", 1));
        make("cafe", "matcha_brigadeiro", "Бригадейро с матчей", GOOD, 60, 20, in("brigadeiro", 1), in("matcha", 1));
        make("cafe", "chocolate_gift_box", "Шоколадная коробка", GOOD, 80, 26, in("chocolate", 2), in("green_tea", 1), in("linen_cloth", 1));
        make("br_kitchen", "tropical_flatbread", "Тропические лепёшки", GOOD, 65, 20, in("cassava_flour", 1), in("cheese", 1), in("olive_oil", 1));
        make("br_kitchen", "moqueca", "Мокека", GOOD, 75, 24, in("fish", 1), in("cassava_flour", 1), in("tomato_sauce", 1));
        make("br_kitchen", "rice_beef_bowl", "Рис с говядиной", GOOD, 70, 22, in("rice", 2), in("beef", 1), in("spice", 1));
        make("br_kitchen", "bolognese", "Паста болоньезе", GOOD, 75, 24, in("pasta", 1), in("beef", 1), in("tomato_sauce", 1));

        make("port", "tropical_festival", "Тропический фестиваль", GOOD, 160, 58, in("coffee_dessert", 1), in("chocolate_cake", 1), in("mango_juice", 1), in("green_tea", 1));
        make("port", "brazil_export_crate", "Контейнер деликатесов", GOOD, 150, 52, in("coffee", 1), in("sugar", 1), in("chocolate_gift_box", 1), in("rice_vinegar", 1));
        make("port", "churrasco_feast", "Чураско-фестиваль", GOOD, 170, 60, in("churrasco", 1), in("moqueca", 1), in("bolognese", 1), in("tropical_flatbread", 1));
        make("port", "coffee_collection", "Кофейная коллекция", GOOD, 130, 44, in("coffee", 1), in("coffee_dessert", 1), in("coffee_bread", 1), in("brigadeiro", 1));
        make("port", "brazil_grand_export", "Золотой экспорт Бразилии", GOOD, 240, 120, in("tropical_festival", 1), in("churrasco_feast", 1), in("eastern_feast", 1), in("brazil_export_crate", 1));

        // ---- Дерево исследований Бразилии ----
        // Этап 2 (требуют культуру)
        res("brazil_coffee", "Кофе", 2, "coffee_roastery", "coffee", cost("coffee_berry", 3), List.of("brazil_crop_coffee_berry"));
        res("brazil_sugar", "Сахар", 2, "sugar_mill", "sugar", cost("sugarcane", 3), List.of("brazil_crop_sugarcane"));
        res("brazil_chocolate", "Шоколад", 2, "chocolate_factory", "chocolate", cost("cocoa_bean", 3), List.of("brazil_crop_cocoa_bean"));
        res("brazil_corn_flour", "Кукурузная мука", 2, "corn_mill", "corn_flour", cost("corn", 3), List.of("brazil_crop_corn"));
        res("brazil_cassava_flour", "Маниоковая мука", 2, "corn_mill", "cassava_flour", cost("cassava", 3), List.of("brazil_crop_cassava"));
        res("brazil_mango_juice", "Манговый сок", 2, "juice_bar", "mango_juice", cost("mango", 3), List.of("brazil_crop_mango"));
        res("brazil_beef", "Говядина", 2, "ranch", "beef", cost("corn", 3), List.of("brazil_crop_corn"));

        // Этап 3 (требуют этап 2)
        res("brazil_cornbread", "Кукурузный хлеб", 3, "br_bakery", "cornbread", cost("corn_flour", 2), List.of("brazil_corn_flour"));
        res("brazil_coffee_bread", "Сладкий кофейный хлеб", 3, "br_bakery", "coffee_bread", cost("coffee", 2), List.of("brazil_coffee"));
        res("brazil_pao_de_queijo", "Пан-ди-кежу", 3, "br_bakery", "pao_de_queijo", cost("cassava_flour", 2), List.of("brazil_cassava_flour"));
        res("brazil_brigadeiro", "Бригадейро", 3, "cafe", "brigadeiro", cost("chocolate", 2), List.of("brazil_chocolate"));
        res("brazil_coffee_latte", "Кофе с молоком", 3, "cafe", "coffee_latte", cost("coffee", 2), List.of("brazil_coffee"));
        res("brazil_churrasco", "Чураско", 3, "churrascaria", "churrasco", cost("beef", 2), List.of("brazil_beef"));
        res("brazil_beef_stew", "Говяжье рагу", 3, "churrascaria", "beef_stew", cost("beef", 2), List.of("brazil_beef", "brazil_cassava_flour"));
        res("brazil_cachaca", "Кашаса (самогон)", 3, "cachaca_still", "cachaca", cost("sugarcane", 3), List.of("brazil_sugar"));

        // Этап 4 (требуют этап 3)
        res("brazil_chocolate_cake", "Шоколадный пирог", 4, "br_bakery", "chocolate_cake", cost("chocolate", 2), List.of("brazil_brigadeiro", "brazil_coffee_bread"));
        res("brazil_coffee_dessert", "Кофейный десерт", 4, "cafe", "coffee_dessert", cost("coffee", 2), List.of("brazil_coffee_latte", "brazil_brigadeiro"));
        res("brazil_chocolate_gift_box", "Шоколадная коробка", 4, "cafe", "chocolate_gift_box", cost("chocolate", 2), List.of("brazil_brigadeiro"));
        res("brazil_moqueca", "Мокека", 4, "br_kitchen", "moqueca", cost("cassava_flour", 2), List.of("brazil_beef_stew", "brazil_pao_de_queijo"));
        res("brazil_bolognese", "Паста болоньезе", 4, "br_kitchen", "bolognese", cost("beef", 2), List.of("brazil_churrasco"));

        // Этап 5 (требуют этап 4)
        res("brazil_tropical_festival", "Тропический фестиваль", 5, "port", "tropical_festival", cost("coffee_dessert", 2), List.of("brazil_coffee_dessert", "brazil_chocolate_cake"));
    }

    // ---------- автоматические исследования ----------

    /**
     * Любой рецепт должен быть «исследуем», иначе его нельзя запустить (Recipe not researched).
     * Для всех продуктов, у которых нет явного res(...), создаём исследование:
     *  - requires = исследования ингредиентов СВОЕЙ страны (чужие достаются торговлей);
     *  - этап = 1 + максимальный этап этих требований (минимум 2);
     *  - цена = главный ингредиент своей страны.
     * Явные res(...) в seedXxx() остаются как есть (они задают «красивый» основной путь).
     */
    private void autoResearch() {
        Map<String, String> regionOfProduct = new HashMap<>();
        for (String key : seededRecipeKeys) {
            ProductRecipe r = recipes.get(key);
            Factory f = factories.get(r.getFactoryCode());
            if (f != null && f.getRegion() != null) regionOfProduct.put(r.getOutput().getCode(), f.getRegion());
        }
        Map<String, Research> byOutput = new HashMap<>();
        for (String key : seededResearchKeys) {
            Research rs = researches.get(key);
            if (rs != null && rs.getOutputCode() != null) byOutput.put(rs.getOutputCode(), rs);
        }
        List<ProductRecipe> pending = new ArrayList<>();
        for (String key : new TreeSet<>(seededRecipeKeys)) {
            ProductRecipe r = recipes.get(key);
            if (!byOutput.containsKey(r.getOutput().getCode())) pending.add(r);
        }

        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (Iterator<ProductRecipe> it = pending.iterator(); it.hasNext(); ) {
                ProductRecipe r = it.next();
                String out = r.getOutput().getCode();
                String region = regionOfProduct.get(out);
                if (region == null) { it.remove(); continue; }

                List<Research> deps = new ArrayList<>();
                ProductRecipe.RecipeInput main = null;
                boolean ready = true;
                for (ProductRecipe.RecipeInput in : r.getInputs()) {
                    if (!region.equals(regionOfProduct.get(in.getCode()))) continue;
                    Research d = byOutput.get(in.getCode());
                    if (d == null) { ready = false; break; }
                    deps.add(d);
                    if (main == null) main = in;
                }
                if (!ready) continue;

                int stage = 2;
                List<String> requires = new ArrayList<>();
                for (Research d : deps) {
                    stage = Math.max(stage, d.getStage() + 1);
                    if (!requires.contains(d.getCode())) requires.add(d.getCode());
                }
                List<Research.Cost> cost = new ArrayList<>();
                if (main != null) {
                    cost.add(cost(main.getCode(), Math.max(2, main.getQty())));
                } else {
                    // нет ингредиентов своей страны: привязываем к первой культуре региона
                    Research crop = null;
                    for (Research x : byOutput.values()) {
                        if (region.equals(x.getRegion()) && x.getStage() == 1
                                && (crop == null || x.getCode().compareTo(crop.getCode()) < 0)) crop = x;
                    }
                    if (crop != null) {
                        requires.add(crop.getCode());
                        cost.add(cost(crop.getOutputCode(), 2));
                    }
                }

                String code = region.toLowerCase() + "_" + out;
                res(region, code, r.getOutput().getTitle(), stage, r.getFactoryCode(), out, cost, requires);
                byOutput.put(out, researches.get(researchKey(region, code)));
                it.remove();
                progress = true;
            }
        }
        for (ProductRecipe r : pending) {
            log.error("SeedData: no research could be generated for {} (unresolved ingredients)", r.getOutput().getCode());
        }
    }

    // ---------- upsert-хелперы ----------

    /** Здание. requiredResearch проставляется позже в linkBuildingsToResearch(). */
    private void building(String title, String code, String region, int w, int h) {
        factory(title, code, Factory.ProductionKind.BUILDING, code, w, h, region, null);
    }

    private void crop(String code, String title, String region, int time, int score, int baseScore) {
        String researchCode = region.toLowerCase() + "_crop_" + code;
        factory(title, code, Factory.ProductionKind.CROP, code, 1, 1, region, researchCode);
        Product p = product(code, title, code, time, score, baseScore, Product.ProductKind.CROP, Product.Legality.LEGAL);
        recipe(code, List.of(), p, time);
        res(region, researchCode, title, 1, code, code, List.of(), List.of());
    }

    private void make(String factoryCode, String code, String title, Product.ProductKind kind,
                      int time, int score, ProductRecipe.RecipeInput... inputs) {
        Product p = product(code, title, code, time, score, 0, kind, Product.Legality.LEGAL);
        recipe(factoryCode, List.of(inputs), p, time);
    }

    private void illegal(String factoryCode, String code, String title, Product.ProductKind kind,
                         int time, int score, ProductRecipe.RecipeInput... inputs) {
        Product p = product(code, title, code, time, score, 0, kind, Product.Legality.ILLEGAL);
        recipe(factoryCode, List.of(inputs), p, time);
    }

    private void factory(String title, String code, Factory.ProductionKind kind,
                         String icon, int w, int h, String region, String requiredResearch) {
        Factory f = factories.get(code);
        if (f == null) {
            f = new Factory();
            f.setCode(code);
        }
        f.setTitle(title);
        f.setKind(kind);
        f.setIcon(icon);
        f.setWidth(w);
        f.setHeight(h);
        f.setRegion(region);
        f.setRequiredResearch(requiredResearch);
        factories.put(code, factoryRepo.save(f));
        seededFactoryCodes.add(code);
    }

    private Product product(String code, String title, String icon,
                            int time, int score, int baseScore, Product.ProductKind kind,
                            Product.Legality legality) {
        Product p = products.get(code);
        if (p == null) {
            p = new Product();
            p.setCode(code);
        }
        p.setTitle(title);
        p.setIcon(icon);
        p.setProductionTime(time);
        p.setScore(score);
        p.setBaseScore(baseScore);
        p.setKind(kind);
        p.setLegality(legality);
        Product saved = productRepo.save(p);
        products.put(code, saved);
        seededProductCodes.add(code);
        return saved;
    }

    private ProductRecipe.RecipeInput in(String code, int qty) {
        return new ProductRecipe.RecipeInput(code, qty);
    }

    private void recipe(String factoryCode, List<ProductRecipe.RecipeInput> inputs,
                        Product output, int time) {
        String key = recipeKey(factoryCode, output.getCode());
        ProductRecipe r = recipes.get(key);
        if (r == null) {
            r = new ProductRecipe();
            r.setFactoryCode(factoryCode);
        }
        r.setOutput(output);
        r.setInputs(new ArrayList<>(inputs));
        r.setOutputQty(1);
        r.setProductionTime(time);
        recipes.put(key, recipeRepo.save(r));
        seededRecipeKeys.add(key);
    }

    private Research.Cost cost(String code, int qty) {
        return new Research.Cost(code, qty);
    }

    private void res(String code, String title, int stage,
                     String factoryCode, String outputCode,
                     Research.Cost cost, List<String> requires) {
        String region = regionFromPrefix(code);
        List<Research.Cost> costs = cost == null ? List.of() : List.of(cost);
        res(region, code, title, stage, factoryCode, outputCode, costs, requires);
    }

    private void res(String region, String code, String title, int stage,
                     String factoryCode, String outputCode,
                     List<Research.Cost> cost, List<String> requires) {
        String key = researchKey(region, code);
        Research r = researches.get(key);
        if (r == null) {
            r = new Research();
            r.setRegion(region);
            r.setCode(code);
        }
        r.setTitle(title);
        r.setStage(stage);
        r.setFactoryCode(factoryCode);
        r.setOutputCode(outputCode);
        r.setCost(new ArrayList<>(cost));
        r.setRequires(new ArrayList<>(requires));
        researches.put(key, researchRepo.save(r));
        if (seededResearchKeys.add(key)) researchOrder.add(key);

        // запоминаем самое раннее исследование для каждого завода
        if (factoryCode != null && outputCode != null) {
            Integer known = unlockStageByFactory.get(factoryCode);
            if (known == null || stage < known) {
                unlockStageByFactory.put(factoryCode, stage);
                unlockResearchByFactory.put(factoryCode, code);
            }
        }
    }

    private static String regionFromPrefix(String code) {
        if (code.startsWith("italy_")) return ITALY;
        if (code.startsWith("japan_")) return JAPAN;
        if (code.startsWith("egypt_")) return EGYPT;
        if (code.startsWith("brazil_")) return BRAZIL;
        throw new IllegalArgumentException("Cannot infer region from research code: " + code);
    }

    // ---------- связка «исследование продукта → здание» и проверка дерева ----------

    /**
     * Здание открывается исследованием, которое НЕ делается в нём самом: берём самое раннее
     * (по этапу, затем по порядку регистрации) исследование продукта этого здания и из его
     * требований выбираем самое «старшее» чужое. Так пекарня открывается мукой, а не хлебом.
     * Этап открывающего исследования всегда ниже этапа продукта, поэтому цикла быть не может.
     */
    private void linkBuildingsToResearch() {
        Map<String, Research> byCode = new HashMap<>();
        for (String key : researchOrder) {
            Research r = researches.get(key);
            if (r != null) byCode.put(r.getCode(), r);
        }
        for (String code : seededFactoryCodes) {
            Factory f = factories.get(code);
            if (f == null || f.getKind() != Factory.ProductionKind.BUILDING || f.getRegion() == null) continue;
            String unlock = pickBuildingUnlock(code, byCode);
            if (unlock == null) continue;
            f.setRequiredResearch(unlock);
            factories.put(f.getCode(), factoryRepo.save(f));
        }
        for (String code : seededFactoryCodes) {
            Factory f = factories.get(code);
            if (f != null && f.getKind() == Factory.ProductionKind.BUILDING
                    && f.getRegion() != null && f.getRequiredResearch() == null) {
                log.warn("SeedData: building {} has no research that opens it (it will be available from the start)", code);
            }
        }
    }

    private String pickBuildingUnlock(String factoryCode, Map<String, Research> byCode) {
        Research base = null;
        String fixed = FINAL_BUILDING_RESEARCH.get(factoryCode);
        if (fixed != null) base = byCode.get(fixed);
        if (base == null) {
            for (String key : researchOrder) {
                Research r = researches.get(key);
                if (r == null || r.getOutputCode() == null || !factoryCode.equals(r.getFactoryCode())) continue;
                if (base == null || r.getStage() < base.getStage()) base = r;
            }
        }
        if (base == null) return null;

        Research best = null;
        for (String reqCode : base.getRequires()) {
            Research dep = byCode.get(reqCode);
            if (dep == null || factoryCode.equals(dep.getFactoryCode())) continue;
            if (best == null
                    || dep.getStage() > best.getStage()
                    || (dep.getStage() == best.getStage() && dep.getCode().compareTo(best.getCode()) < 0)) {
                best = dep;
            }
        }
        // ингредиентов нет (культура без цепочки) — откроется исследованием самого продукта
        return best != null ? best.getCode() : base.getCode();
    }

    /** Этап N (>=2) обязан требовать хотя бы одно исследование этапа N-1 и ничего с этапа >= N. */
    private void validateResearchTree() {
        Map<String, Research> byCode = new HashMap<>();
        for (String key : seededResearchKeys) {
            Research r = researches.get(key);
            if (r != null) byCode.put(r.getCode(), r);
        }
        int problems = 0;
        for (Research r : byCode.values()) {
            if (r.getStage() <= 1) continue;
            boolean hasPrevStage = false;
            for (String req : r.getRequires()) {
                Research dep = byCode.get(req);
                if (dep == null) {
                    log.error("SeedData research tree: {} requires unknown research {}", r.getCode(), req);
                    problems++;
                    continue;
                }
                if (dep.getStage() >= r.getStage()) {
                    log.error("SeedData research tree: {} (stage {}) requires {} (stage {})",
                            r.getCode(), r.getStage(), dep.getCode(), dep.getStage());
                    problems++;
                }
                if (dep.getStage() == r.getStage() - 1) hasPrevStage = true;
            }
            if (!hasPrevStage) {
                log.error("SeedData research tree: {} (stage {}) has no prerequisite from stage {}",
                        r.getCode(), r.getStage(), r.getStage() - 1);
                problems++;
            }
        }
        if (problems == 0) log.info("SeedData research tree: OK ({} nodes)", byCode.size());
        else log.error("SeedData research tree: {} problem(s) found", problems);
    }

    // ---------- чистка устаревшего ----------

    private void pruneRecipes() {
        for (Map.Entry<String, ProductRecipe> e : new HashMap<>(recipes).entrySet()) {
            if (!seededRecipeKeys.contains(e.getKey())) {
                log.info("SeedData: removing stale recipe {}", e.getKey());
                recipeRepo.delete(e.getValue());
                recipes.remove(e.getKey());
            }
        }
    }

    private void pruneCatalog() {
        for (Factory f : new ArrayList<>(factories.values())) {
            if (!seededFactoryCodes.contains(f.getCode())) {
                try {
                    factoryRepo.delete(f);
                    factories.remove(f.getCode());
                    log.info("SeedData: removed stale factory {}", f.getCode());
                } catch (Exception ex) {
                    log.warn("SeedData: cannot remove factory {}: {}", f.getCode(), ex.getMessage());
                }
            }
        }
        for (Product p : new ArrayList<>(products.values())) {
            if (!seededProductCodes.contains(p.getCode())) {
                try {
                    productRepo.delete(p);
                    products.remove(p.getCode());
                    log.info("SeedData: removed stale product {}", p.getCode());
                } catch (Exception ex) {
                    log.warn("SeedData: cannot remove product {}: {}", p.getCode(), ex.getMessage());
                }
            }
        }
    }

    private void pruneResearch() {
        for (Map.Entry<String, Research> e : new HashMap<>(researches).entrySet()) {
            if (!seededResearchKeys.contains(e.getKey())) {
                log.info("SeedData: removing stale research {}", e.getKey());
                researchRepo.delete(e.getValue());
                researches.remove(e.getKey());
            }
        }
    }

    private static String recipeKey(String factoryCode, String outputCode) {
        return factoryCode + "->" + outputCode;
    }

    private static String researchKey(String region, String code) {
        return region + "/" + code;
    }
}