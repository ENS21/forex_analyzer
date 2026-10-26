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

/**
 * Компонент для хранения в оперативной памяти актуальных рыночных данных (тиков).
 * Используется для быстрого доступа к текущим ценам без необходимости запрашивать базу данных.
 * Класс является потокобезопасным (thread-safe) благодаря использованию ConcurrentHashMap.
 */
@Component
public class TickCache {
    private static final Logger log = LoggerFactory.getLogger(TickCache.class);
    // Часовой пояс сервера брокера
    private static final ZoneId BROKER_ZONE = ZoneOffset.ofHours(3);
    // ===== Вложенный класс: последний тик =====
    /**
     * Неизменяемый объект, представляющий собой один тик (снимок цен bid/ask).
     */
    // Вложенный класс Tick (добавили rsiH1)
    public static class Tick {
        private final double bid;
        private final double ask;
        private final long time;
        private final Double rsiD1;
        private final Double rsiH4;
        private final Double rsiH1; // <--- ДОБАВИЛИ ПОЛЕ

        // Конструктор теперь принимает 6 параметров
        public Tick(double bid, double ask, long time, Double rsiD1, Double rsiH4, Double rsiH1) {
            this.bid = bid;
            this.ask = ask;
            this.time = time;
            this.rsiD1 = rsiD1;
            this.rsiH4 = rsiH4;
            this.rsiH1 = rsiH1;
        }

        public double getBid() { return bid; }
        public double getAsk() { return ask; }
        public long getTime() { return time; }
        public Double getRsiD1() { return rsiD1; }
        public Double getRsiH4() { return rsiH4; }
        public Double getRsiH1() { return rsiH1; } // <--- ДОБАВИЛИ ГЕТТЕР
    }

    // Метод update (добавили параметр rsiH1)
    public void update(String symbol, double bid, double ask, long timeUnix, double dayOpenFromMT5, Double rsiD1, Double rsiH4, Double rsiH1) {
        // Передаем rsiH1 в конструктор Tick
        lastTicks.put(symbol, new Tick(bid, ask, timeUnix, rsiD1, rsiH4, rsiH1));

        LocalDate tickDate = Instant.ofEpochSecond(timeUnix)
                .atZone(BROKER_ZONE)
                .toLocalDate();

        DailyOpen existing = dailyOpens.get(symbol);

        if (existing == null || !existing.getDate().equals(tickDate)) {
            double openPrice = dayOpenFromMT5 > 0 ? dayOpenFromMT5 : bid;
            dailyOpens.put(symbol, new DailyOpen(openPrice, tickDate));
            log.info("Открытие дня для {} = {} (дата {})", symbol, openPrice, tickDate);
        } else {
            existing.updateExtremes(bid);
            if (dayOpenFromMT5 > 0 && Math.abs(existing.getPrice() - dayOpenFromMT5) > 0.00001) {
                dailyOpens.put(symbol, new DailyOpen(dayOpenFromMT5, tickDate));
                log.info("Обновлено открытие дня для {} = {}", symbol, dayOpenFromMT5);
            }
        }
    }

    // ===== Вложенный класс: открытие дня =====
    /**
     * Объект, хранящий статистику торгового дня: цену открытия, а также текущие максимум и минимум.
     */
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

        /**
         * Обновляет максимум и минимум дня на основе новой цены.
         * @param bid Текущая цена (Bid).
         */
        public void updateExtremes(double bid) {
            if (bid > high) high = bid;
            if (bid < low) low = bid;
        }
    }

    // ===== Поля главного класса =====
    private final Map<String, Tick> lastTicks = new ConcurrentHashMap<>();
    private final Map<String, DailyOpen> dailyOpens = new ConcurrentHashMap<>();

    // ===== Методы главного класса =====
    /**
     * Обновляет кэш новыми данными о тике.
     * Метод автоматически определяет, наступил ли новый торговый день (согласно часовому поясу брокера),
     * и сбрасывает статистику дня при необходимости.
     *
     * @param symbol        Название инструмента (например, "EURUSD").
     * @param bid           Текущая цена Bid.
     * @param ask           Текущая цена Ask.
     * @param timeUnix      Время тика в формате Unix Timestamp (секунды).
     * @param dayOpenFromMT5 Цена открытия дня, переданная от терминала MT5 (0, если неизвестна).
     */
    /**public void update(String symbol, double bid, double ask, long timeUnix, double dayOpenFromMT5, Double rsiD1, Double rsiH4, Double rsiH1) {
        // 1. Последний тик
        lastTicks.put(symbol, new Tick(bid, ask, timeUnix, rsiD1, rsiH4, rsiH1));

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
    }*/

    /**
     * Возвращает последний полученный тик для указанного инструмента.
     * @param symbol Название инструмента.
     * @return Объект {@link Tick} или null, если данных еще нет.
     */

    public Tick getLast(String symbol) {
        return lastTicks.get(symbol);
    }

    /**
     * Возвращает текущую цену (Bid) для указанного инструмента.
     * @param symbol Название инструмента.
     * @return Текущая цена или null, если данных еще нет.
     */
    public Double getCurrentPrice(String symbol) {
        Tick tick = lastTicks.get(symbol);
        return tick != null ? tick.getBid() : null;
    }

    /**
     * Возвращает цену открытия текущего торгового дня.
     * @param symbol Название инструмента.
     * @return Цена открытия или null, если данных еще нет.
     */
    public Double getOpenPrice(String symbol) {
        DailyOpen dailyOpen = dailyOpens.get(symbol);
        return dailyOpen != null ? dailyOpen.getPrice() : null;
    }

    /**
     * Возвращает максимальную цену за текущий торговый день.
     * @param symbol Название инструмента.
     * @return Максимальная цена или null, если данных еще нет.
     */
    public Double getDayHigh(String symbol) {
        DailyOpen dailyOpen = dailyOpens.get(symbol);
        return dailyOpen != null ? dailyOpen.getHigh() : null;
    }

    /**
     * Возвращает минимальную цену за текущий торговый день.
     * @param symbol Название инструмента.
     * @return Минимальная цена или null, если данных еще нет.
     */
    public Double getDayLow(String symbol) {
        DailyOpen dailyOpen = dailyOpens.get(symbol);
        return dailyOpen != null ? dailyOpen.getLow() : null;
    }
}