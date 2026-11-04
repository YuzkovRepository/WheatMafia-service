package com.example.example.dto;

import java.util.List;
import java.util.Map;

public class DeputyDto {

    /** Один «пункт» заказа депутата: блюдо этапа (grand=false) или золотой экспорт (grand=true). */
    public record Step(String slot, int stage, boolean grand, boolean done,
                       String productCode, String productTitle, List<String> examples) {}

    public record Player(Long userId, String nickName, String region, String grandCode,
                         List<Step> steps, int delivered, int total, boolean finished) {}

    public record Winner(Long userId, String nickName, String region) {}

    /** dishStage: код блюда -> этап (по нему клиент находит подходящие блюда на складе). */
    public record State(String sessionCode, String status, List<Player> players,
                        Winner winner, Map<String, Integer> dishStage) {}

    /** type: FED — кто-то сдал блюдо, WON — кто-то сдал золотой экспорт и победил. */
    public record Event(String type, Long userId, String nickName,
                        String productCode, String productTitle, String slot, State state) {}
}