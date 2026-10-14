package com.ekaterina.forex.forex_analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TickCache {

    private static final Logger log = LoggerFactory.getLogger(TickCache.class);

    // Часовой пояс сервера брокера (Альфа-Форекс — GMT+3 обычно)
    private static final ZoneId BROKER_ZONE = ZoneOffset.ofHours(3);

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

    public static class DailyOpen {
        private final double price;
        private final LocalDate date;

        public DailyOpen(double price, LocalDate date) {
            this.price = price;
            this.date = date;
        }

        public double getPrice() { return price; }
        public LocalDate getDate() { return date; }
    }

    private final Map<String, Tick> lastTicks = new ConcurrentHashMap<>();
    private final Map<String, DailyOpen> dailyOpens = new ConcurrentHashMap<>();

    public void update(String symbol, double bid, double ask, long timeUnix) {
        // 1. Обновляем последний тик
        lastTicks.put(symbol, new Tick(bid, ask, timeUnix));

        // 2. Проверяем, не новый ли это день
        LocalDate tickDate = Instant.ofEpochSecond(timeUnix)
                .atZone(BROKER_ZONE)
                .toLocalDate();

        DailyOpen existing = dailyOpens.get(symbol);
        if (existing == null || !existing.getDate().equals(tickDate)) {
            dailyOpens.put(symbol, new DailyOpen(bid, tickDate));
            log.info("Открытие дня для {} = {} (дата {})", symbol, bid, tickDate);
        }
    }

    public Tick getLast(String symbol) {
        return lastTicks.get(symbol);
    }

    public Double getCurrentPrice(String symbol) {
        Tick tick = lastTicks.get(symbol);
        return tick != null ? tick.getBid() : null;
    }

    public Double getOpenPrice(String symbol) {
        DailyOpen dailyOpen = dailyOpens.get(symbol);
        return dailyOpen != null ? dailyOpen.getPrice() : null;
    }
}
