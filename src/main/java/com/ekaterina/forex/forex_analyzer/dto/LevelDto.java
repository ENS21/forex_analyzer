package com.ekaterina.forex.forex_analyzer.dto;

public class LevelDto {
    private final String type;      // "SUPPORT" или "RESISTANCE"
    private final double price;
    private final String time;      // дата свечи, где найден уровень

    public LevelDto(String type, double price, String time) {
        this.type = type;
        this.price = price;
        this.time = time;
    }

    public String getType() { return type; }
    public double getPrice() { return price; }
    public String getTime() { return time; }
}