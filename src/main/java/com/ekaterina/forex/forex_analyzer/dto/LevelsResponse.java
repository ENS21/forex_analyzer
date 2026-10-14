package com.ekaterina.forex.forex_analyzer.dto;

import java.util.List;

public class LevelsResponse {

    private String pair;
    private String timeframe;
    private int candlesAnalyzed;
    private double currentPrice;
    private int levelsCount;
    private List<LevelDto> levels;

    public LevelsResponse() {}

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public int getCandlesAnalyzed() { return candlesAnalyzed; }
    public void setCandlesAnalyzed(int candlesAnalyzed) { this.candlesAnalyzed = candlesAnalyzed; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public int getLevelsCount() { return levelsCount; }
    public void setLevelsCount(int levelsCount) { this.levelsCount = levelsCount; }

    public List<LevelDto> getLevels() { return levels; }
    public void setLevels(List<LevelDto> levels) { this.levels = levels; }
}