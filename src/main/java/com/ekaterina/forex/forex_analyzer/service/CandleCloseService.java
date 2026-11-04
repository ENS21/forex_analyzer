package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CandleCloseService {

    private static final Logger log = LoggerFactory.getLogger(CandleCloseService.class);

    private final AnalyticsService analyticsService;
    private final CandleRepository candleRepository;
    private final TrendService trendService;
    private final MaxNotificationService maxNotificationService;

    public CandleCloseService(AnalyticsService analyticsService,
                              CandleRepository candleRepository,
                              TrendService trendService,
                              MaxNotificationService maxNotificationService) {
        this.analyticsService = analyticsService;
        this.candleRepository = candleRepository;
        this.trendService = trendService;
        this.maxNotificationService = maxNotificationService;
    }

    public void onCandleClosed(String symbol, String timeframe) {
        List<Candle> candles = candleRepository.findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, timeframe);
        if (candles.size() < 50) return;

        Collections.reverse(candles);

        // 1. Считаем новый тренд
        String newTrend = analyticsService.calcTrend(candles);

        // 2. Смотрим, какой тренд был в памяти
        String oldTrend = trendService.getTrend(symbol, timeframe);

        // 3. ЛОГИКА ОТПРАВКИ:
        if (oldTrend == null) {
            // ПЕРВЫЙ ЗАПУСК: Тренда в памяти нет. Запоминаем его, но НЕ пишем "Смена тренда".
            // Шлем одно сообщение, чтобы ты поняла, что программа работает.
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
            // ТРЕНД ИЗМЕНИЛСЯ (был один, стал другой)
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
            // ТРЕНД НЕ ИЗМЕНИЛСЯ: ничего не шлем, просто пишем в логи IDEA
            log.debug("Свеча {} {} закрылась. Тренд остался прежним: {}", symbol, timeframe, newTrend);
        }
    }
}