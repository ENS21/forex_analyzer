package com.ekaterina.forex.forex_analyzer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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

    public AnalysisResponse() {}

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public int getCandlesCount() { return candlesCount; }
    public void setCandlesCount(int candlesCount) { this.candlesCount = candlesCount; }

    public String getLastCandleTime() { return lastCandleTime; }
    public void setLastCandleTime(String lastCandleTime) { this.lastCandleTime = lastCandleTime; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getOpenPrice() { return openPrice; }
    public void setOpenPrice(double openPrice) { this.openPrice = openPrice; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    public double getRsi() { return rsi; }
    public void setRsi(double rsi) { this.rsi = rsi; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public double getDayHigh() { return dayHigh; }
    public void setDayHigh(double dayHigh) { this.dayHigh = dayHigh; }

    public double getDayLow() { return dayLow; }
    public void setDayLow(double dayLow) { this.dayLow = dayLow; }

    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }

    public Double getNearestSupport() { return nearestSupport; }
    public void setNearestSupport(Double nearestSupport) { this.nearestSupport = nearestSupport; }

    public Double getNearestResistance() { return nearestResistance; }
    public void setNearestResistance(Double nearestResistance) { this.nearestResistance = nearestResistance; }
}