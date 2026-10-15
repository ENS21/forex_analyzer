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
    // Часовой пояс сервера брокера
    private static final ZoneId BROKER_ZONE = ZoneOffset.ofHours(3);
    // ===== Вложенный класс: последний тик =====
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

    // ===== Вложенный класс: открытие дня =====
    public static class DailyOpen {
        private final double price;
        private final LocalDate date;
        private double high;
        private double low;

        public DailyOpen(double price, LocalDate date) {
            this.price = price;
            this.date = date;
            this.high = price;
            this.low = price;
        }

        public double getPrice() { return price; }
        public LocalDate getDate() { return date; }
        public double getHigh() { return high; }
        public double getLow() { return low; }

        public void updateExtremes(double bid) {
            if (bid > high) high = bid;
            if (bid < low) low = bid;
        }
    }

    // ===== Поля главного класса =====
    private final Map<String, Tick> lastTicks = new ConcurrentHashMap<>();
    private final Map<String, DailyOpen> dailyOpens = new ConcurrentHashMap<>();

    // ===== Методы главного класса =====

    public void update(String symbol, double bid, double ask, long timeUnix, double dayOpenFromMT5) {
        // 1. Последний тик
        lastTicks.put(symbol, new Tick(bid, ask, timeUnix));

        // 2. Дата
        LocalDate tickDate = Instant.ofEpochSecond(timeUnix)
                .atZone(BROKER_ZONE)
                .toLocalDate();

        // 3. Открытие дня
        DailyOpen existing = dailyOpens.get(symbol);

        if (existing == null || !existing.getDate().equals(tickDate)) {
            // Новый день — сохраняем открытие
            double openPrice = dayOpenFromMT5 > 0 ? dayOpenFromMT5 : bid;
            dailyOpens.put(symbol, new DailyOpen(openPrice, tickDate));
            log.info("Открытие дня для {} = {} (дата {})", symbol, openPrice, tickDate);
        } else {
            // Тот же день — обновляем high/low
            existing.updateExtremes(bid);

            // Если пришло более точное dayOpen — обновим (для случая перезапуска)
            if (dayOpenFromMT5 > 0 && Math.abs(existing.getPrice() - dayOpenFromMT5) > 0.00001) {
                dailyOpens.put(symbol, new DailyOpen(dayOpenFromMT5, tickDate));
                log.info("Обновлено открытие дня для {} = {}", symbol, dayOpenFromMT5);
            }
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

    // ← вот эти два метода добавляем в главный класс
    public Double getDayHigh(String symbol) {
        DailyOpen dailyOpen = dailyOpens.get(symbol);
        return dailyOpen != null ? dailyOpen.getHigh() : null;
    }

    public Double getDayLow(String symbol) {
        DailyOpen dailyOpen = dailyOpens.get(symbol);
        return dailyOpen != null ? dailyOpen.getLow() : null;
    }
}