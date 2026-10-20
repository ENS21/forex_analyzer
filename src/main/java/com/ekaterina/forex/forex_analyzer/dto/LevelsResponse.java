package com.ekaterina.forex.forex_analyzer.dto;

import java.util.List;
import lombok.*;
/**
 * DTO для ответа API со списком найденных уровней поддержки и сопротивления.
 * Содержит метаданные запроса и сам список уровней.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class LevelsResponse {

    private String pair;
    private String timeframe;
    private int candlesAnalyzed;
    private double currentPrice;
    private int levelsCount;
    private List<LevelDto> levels;
}