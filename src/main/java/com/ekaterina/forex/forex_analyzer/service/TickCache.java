package com.ekaterina.forex.forex_analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TickCache {
    private static final Logger log = LoggerFactory.getLogger(TickCache.class);
    public static class Tick {
        private final double bid;
        private final double ask;
        private final long time;
        public Tick(double bid, double ask, long time) {
            this.bid = bid;
            this.ask = ask;
            this.time = time;
        }
        public double getBid() { return bid; }
        public double getAsk() { return ask; }
        public long getTime() { return time; }
    }

    private final Map<String, Tick> lastTicks = new ConcurrentHashMap<>();
    public void update(String symbol, double bid, double ask, long time) {
        lastTicks.put(symbol, new Tick(bid, ask, time));
    }
    public Tick getLast(String symbol) {
        return lastTicks.get(symbol);
    }

    public Double getCurrentPrice(String symbol) {
        Tick tick = lastTicks.get(symbol);
        if (tick == null) {
            log.warn("Нет данных о тике для {}", symbol);
            return null;
        }
        return tick.getBid();
    }
}