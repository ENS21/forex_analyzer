package com.ekaterina.forex.forex_analyzer.dto;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * Ответ API с результатами технического анализа валютной пары.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Schema(description = "Аналитика по валютной паре: RSI, цена, изменение за день")
public class AnalysisResponse {

    @Schema(description = "Валютная пара", example = "EURUSD")
    private String pair;

    @Schema(description = "Таймфрейм", example = "D1")
    private String timeframe;

    @Schema(description = "Сколько свечей использовано для расчёта", example = "100")
    private int candlesCount;

    @Schema(description = "Время последней свечи в базе", example = "2026-10-13T00:00")
    private String lastCandleTime;

    @Schema(description = "Текущая цена (из последнего тика", example = "1.11970")
    private double currentPrice;

    @Schema(description = "Цена открытия дня", example = "1.11539")
    private double openPrice;

    @Schema(description = "Изменение цены за день (абсолютное", example = "0.00431")
    private double change;

    @Schema(description = "Изменение цены за день (%", example = "0.39")
    private double changePercent;

    @Schema(description = "Значение RSI (0–100", example = "30.40")
    private double rsi;

    @Schema(description = "Сигнал: ПЕРЕКУПЛЕНА / ПЕРЕПРОДАНА / НЕЙТРАЛЬНО", example = "НЕЙТРАЛЬНО")
    private String signal;

    @Schema(description = "Максимум дня", example = "1.11350")
    private double dayHigh;

    @Schema(description = "Минимум дня", example = "1.11100")
    private double dayLow;

    @Schema(description = "Тренд: ВОСХОДЯЩИЙ / НИСХОДЯЩИЙ / БОКОВОЙ", example = "БОКОВОЙ")
    private String trend;

    @Schema(description = "Ближайший уровень поддержки (ниже цены", example = "1.11000")
    private Double nearestSupport;

    @Schema(description = "Ближайший уровень сопротивления (выше цены", example = "1.11500")
    private Double nearestResistance;
}