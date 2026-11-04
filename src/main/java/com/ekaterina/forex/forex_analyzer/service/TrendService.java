package com.ekaterina.forex.forex_analyzer.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrendService {

    // Ключ: "EURUSD_H4", Значение: "НИСХОДЯЩИЙ"
    private final Map<String, String> trends = new ConcurrentHashMap<>();

    public String getTrend(String symbol, String timeframe) {
        return trends.get(symbol + "_" + timeframe);
    }

    public void updateTrend(String symbol, String timeframe, String newTrend) {
        trends.put(symbol + "_" + timeframe, newTrend);
    }
}