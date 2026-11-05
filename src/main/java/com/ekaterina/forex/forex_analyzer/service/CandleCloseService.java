package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CandleCloseService {

    private static final Logger log = LoggerFactory.getLogger(CandleCloseService.class);

    private final AnalyticsService analyticsService;
    private final CandleRepository candleRepository;
    private final TrendService trendService;
    private final MaxNotificationService maxNotificationService;

    // ДОБАВИЛИ НОВЫЕ СЕРВИСЫ ДЛЯ ATR
    private final AtrService atrService;
    private final TickCache tickCache;

    // Чтобы не спамить предупреждением об ATR, запоминаем, кому мы уже отправили его сегодня
    private final Map<String, Boolean> atrWarningSentToday = new ConcurrentHashMap<>();

    public CandleCloseService(AnalyticsService analyticsService,
                              CandleRepository candleRepository,
                              TrendService trendService,
                              MaxNotificationService maxNotificationService,
                              AtrService atrService,   // ДОБАВИЛИ В КОНСТРУКТОР
                              TickCache tickCache) {   // ДОБАВИЛИ В КОНСТРУКТОР
        this.analyticsService = analyticsService;
        this.candleRepository = candleRepository;
        this.trendService = trendService;
        this.maxNotificationService = maxNotificationService;
        this.atrService = atrService;
        this.tickCache = tickCache;
    }

    public void onCandleClosed(String symbol, String timeframe) {

        // =========================================================================
        // ЛОГИКА 1: ПРОВЕРКА ATR (Рынок прошел 70% от среднего ATR)
        // =========================================================================
        Double progress = atrService.calculateTodayAtrProgress(symbol, tickCache);

        if (progress != null && progress >= 70.0) {
            if (!atrWarningSentToday.getOrDefault(symbol, false)) {
                String warningMessage = String.format(
                        "⚠️ **ВНИМАНИЕ! ОТРАБОТКА ATR** ⚠️\n\n" +
                                "Пара: %s\n" +
                                "Цена уже прошла **%.1f%%** от среднего дневного ATR (за 22 дня).\n\n" +
                                "Высокая вероятность консолидации. Будьте осторожны с новыми входами!",
                        symbol, progress
                );
                maxNotificationService.sendSignal(warningMessage);
                atrWarningSentToday.put(symbol, true);
                log.info("Отправлено предупреждение об ATR для {} (пройдено {}%)", symbol, progress);
            }
        }

        // =========================================================================
        // ЛОГИКА 2: СМЕНА ТРЕНДА (твой старый код)
        // =========================================================================
        List<Candle> candles = candleRepository.findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, timeframe);
        if (candles.size() < 50) return;

        Collections.reverse(candles);

        String newTrend = analyticsService.calcTrend(candles);
        String oldTrend = trendService.getTrend(symbol, timeframe);

        if (oldTrend == null) {
            trendService.updateTrend(symbol, timeframe, newTrend);
            String message = String.format(
                    "🤖 **Forex Analyzer Запущен**\n\n" +
                            "Первый расчет тренда:\n" +
                            "Пара: %s\n" +
                            "Таймфрейм: %s\n" +
                            "Установлен тренд: **%s**\n\n" +
                            "Дальше буду писать только при смене!",
                    symbol, timeframe, newTrend
            );
            maxNotificationService.sendSignal(message);
            log.info("Первичная установка тренда {} {}: {}", symbol, timeframe, newTrend);

        } else if (!oldTrend.equals(newTrend)) {
            trendService.updateTrend(symbol, timeframe, newTrend);
            String message = String.format(
                    "🚨 **СМЕНА ТРЕНДА!** 🚨\n\n" +
                            "Пара: %s\n" +
                            "Таймфрейм: %s\n" +
                            "Был: %s\n" +
                            "Стал: **%s**",
                    symbol, timeframe, oldTrend, newTrend
            );
            maxNotificationService.sendSignal(message);
            log.info("Смена тренда {} {}: {} -> {}", symbol, timeframe, oldTrend, newTrend);

        } else {
            log.debug("Свеча {} {} закрылась. Тренд остался прежним: {}", symbol, timeframe, newTrend);
        }
    }
}