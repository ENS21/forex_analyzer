package com.ekaterina.forex.forex_analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для отслеживания рыночных событий (RSI) и отправки каскадных уведомлений в MAX.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final TickCache tickCache;
    private final MaxNotificationService maxNotificationService;

    // Запоминаем последний пробитый уровень (в процентах).
    // Например, если тут лежит 30, значит мы уже отправили сигнал при падении до 30.
    private final Map<String, Integer> lastOversoldLevel = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastOverboughtLevel = new ConcurrentHashMap<>();

    // Наши пороги для срабатывания
    private static final int[] OVERSOLD_LEVELS = {30, 25, 20, 15};
    private static final int[] OVERBOUGHT_LEVELS = {70, 75, 80, 85};

    public AlertService(TickCache tickCache, MaxNotificationService maxNotificationService) {
        this.tickCache = tickCache;
        this.maxNotificationService = maxNotificationService;
    }

    public void checkAndSendSignals(String symbol) {
        TickCache.Tick tick = tickCache.getLast(symbol);
        if (tick == null) return;

        // Проверяем D1, H4 и добавляем H1
        checkTimeframe(symbol, "D1", tick.getRsiD1(), tick.getBid());
        checkTimeframe(symbol, "H4", tick.getRsiH4(), tick.getBid());
        checkTimeframe(symbol, "H1", tick.getRsiH1(), tick.getBid()); // НОВАЯ СТРОКА
    }

    private void checkTimeframe(String symbol, String timeframe, Double rsi, double currentPrice) {
        if (rsi == null || rsi == 0.0) return;

        String key = symbol + "_" + timeframe;

        // --- ЛОГИКА ДЛЯ ПЕРЕПРОДАННОСТИ (Ниже 30) ---
        if (rsi <= 30) {
            int lastLevel = lastOversoldLevel.getOrDefault(key, 0);
            int crossedLevel = 0;

            // Проверяем, пробили ли мы новый порог
            for (int level : OVERSOLD_LEVELS) {
                if (rsi <= level && (lastLevel == 0 || level < lastLevel)) {
                    crossedLevel = level; // Мы пробили этот уровень
                    break;
                }
            }

            if (crossedLevel != 0) {
                sendAlertMessage(symbol, timeframe, rsi, currentPrice, "ПЕРЕПРОДАНА", crossedLevel);
                lastOversoldLevel.put(key, crossedLevel); // Запоминаем, что дошли до этого уровня
            }
        }
        // Если RSI вернулся выше 30, сбрасываем память, чтобы в следующий раз снова слать сигналы
        else {
            lastOversoldLevel.put(key, 0);
        }

        // --- ЛОГИКА ДЛЯ ПЕРЕКУПЛЕННОСТИ (Выше 70) ---
        if (rsi >= 70) {
            int lastLevel = lastOverboughtLevel.getOrDefault(key, 0);
            int crossedLevel = 0;

            for (int level : OVERBOUGHT_LEVELS) {
                if (rsi >= level && (lastLevel == 0 || level > lastLevel)) {
                    crossedLevel = level;
                }
            }

            if (crossedLevel != 0) {
                sendAlertMessage(symbol, timeframe, rsi, currentPrice, "ПЕРЕКУПЛЕНА", crossedLevel);
                lastOverboughtLevel.put(key, crossedLevel);
            }
        } else {
            lastOverboughtLevel.put(key, 0);
        }
    }

    private void sendAlertMessage(String symbol, String timeframe, double rsi, double price, String state, int level) {
        String emoji = state.equals("ПЕРЕКУПЛЕНА") ? "🔴" : "🟢";
        String action = state.equals("ПЕРЕКУПЛЕНА") ? "выше" : "ниже";

        // Формируем красивое сообщение с указанием конкретного пробитого уровня
        String message = String.format(
                "🚨 **ВНИМАНИЕ!** 🚨\n\n" +
                        "%s %s на %s\n" +
                        "📉 RSI опустился %s %d и сейчас равен: **%.1f**\n" +
                        "💵 Цена: %.5f\n\n" +
                        "⚠️ Возможна сильная реакция цены.\n" +
                        "🤖 Forex Ana lyzer",
                emoji, symbol, timeframe, action, level, rsi, price
        );

        maxNotificationService.sendSignal(message);
        log.info("Отправлен каскадный сигнал: {} {} RSI {:.1f} (пробит уровень {})", symbol, timeframe, rsi, level);
    }
}