package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.BaseBar;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;

@Service
public class AnalyticsService {
    private final TickCache tickCache;
    private final CandleRepository candleRepository;
    public AnalyticsService(CandleRepository candleRepository, TickCache tickCache) {
        this.tickCache = tickCache;
        this.candleRepository = candleRepository;
    }
    /**
     * Считает RSI по свечам из базы данных.
     */
    public Map<String, Object> getRsiAnalysis(String symbol, String timeframe) {
        // Для RSI достаточно 100 последних свечей (14 для расчёта + запас)
        List<Candle> candles = candleRepository
                .findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, timeframe);
        Collections.reverse(candles);  // развернуть: старые → новые

        if (candles.size() < 15) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Недостаточно свечей для расчёта RSI (нужно минимум 15)");
            error.put("count", candles.size());

            return error;
        }

        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol + "_" + timeframe)
                .withNumTypeOf(DoubleNum.class)
                .build();

        for (Candle candle : candles) {
            ZonedDateTime endTime = candle.getOpenTime().atZone(ZoneId.systemDefault());
            series.addBar(new BaseBar(
                    Duration.ofDays(1),
                    endTime,
                    candle.getOpen().doubleValue(),
                    candle.getHigh().doubleValue(),
                    candle.getLow().doubleValue(),
                    candle.getClose().doubleValue(),
                    candle.getVolume().doubleValue()
            ));
        }

        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 14);
        double rsiValue = rsi.getValue(series.getEndIndex()).doubleValue();

        Candle lastCandle = candles.get(candles.size() - 1);

        Map<String, Object> result = new HashMap<>();
        result.put("pair", symbol);
        result.put("timeframe", timeframe);
        result.put("candlesCount", candles.size());
        result.put("lastCandleTime", lastCandle.getOpenTime().toString());
        Double livePrice = tickCache.getCurrentPrice(symbol);
        result.put("currentPrice", livePrice != null ? livePrice : lastCandle.getClose());
        //result.put("currentPrice", lastCandle.getClose());
        result.put("rsi", Math.round(rsiValue * 100.0) / 100.0);
        result.put("signal",
                rsiValue > 70 ? "ПЕРЕКУПЛЕНА"
                        : rsiValue < 30 ? "ПЕРЕПРОДАНА"
                        : "НЕЙТРАЛЬНО");
        return result;
    }
    public Map<String, Object> findLevels(String symbol, String timeframe) {
        // 1. Берём последние 200 свечей
        List<Candle> candles = candleRepository
                .findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, timeframe);
        Collections.reverse(candles);

        if (candles.size() < 20) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Недостаточно свечей для анализа уровней");
            error.put("count", candles.size());
            return error;
        }

        // 2. Ищем локальные max/min
        // Свеча — локальный максимум, если её high больше, чем у 3 соседей справа и слева
        // 2. Ищем локальные max/min
        // 2. Ищем локальные max/min (без типа — тип определим позже, по текущей цене)
        int lookback = 3;
        List<Double> rawPrices = new ArrayList<>();
        List<String> rawTimes = new ArrayList<>();

        for (int i = lookback; i < candles.size() - lookback; i++) {
            Candle c = candles.get(i);

            boolean isLocalMax = true;
            boolean isLocalMin = true;

            for (int j = 1; j <= lookback; j++) {
                if (candles.get(i - j).getHigh().doubleValue() >= c.getHigh().doubleValue()
                        || candles.get(i + j).getHigh().doubleValue() >= c.getHigh().doubleValue()) {
                    isLocalMax = false;
                }
                if (candles.get(i - j).getLow().doubleValue() <= c.getLow().doubleValue()
                        || candles.get(i + j).getLow().doubleValue() <= c.getLow().doubleValue()) {
                    isLocalMin = false;
                }
            }

            if (isLocalMax) {
                rawPrices.add(c.getHigh().doubleValue());
                rawTimes.add(c.getOpenTime().toString());
            }
            if (isLocalMin) {
                rawPrices.add(c.getLow().doubleValue());
                rawTimes.add(c.getOpenTime().toString());
            }
        }

        // 3. Группируем близкие уровни (в пределах 0.15%)
        // 3. Группируем близкие уровни (в пределах 0.15%)
        double tolerance = 0.0015;
        List<Double> mergedPrices = new ArrayList<>();
        List<String> mergedTimes = new ArrayList<>();

        for (int k = 0; k < rawPrices.size(); k++) {
            double price = rawPrices.get(k);
            boolean found = false;
            for (double existing : mergedPrices) {
                if (Math.abs(existing - price) / existing < tolerance) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                mergedPrices.add(price);
                mergedTimes.add(rawTimes.get(k));
            }
        }
        // 4. Текущая цена — она определит тип уровней
        Double livePriceObj = tickCache.getCurrentPrice(symbol);
        double livePrice = livePriceObj != null
                ? livePriceObj
                : candles.get(candles.size() - 1).getClose().doubleValue();

// 5. Создаём LevelDto с правильным типом
        List<LevelDto> levels = new ArrayList<>();
        for (int k = 0; k < mergedPrices.size(); k++) {
            double price = mergedPrices.get(k);
            String type = price > livePrice ? "RESISTANCE" : "SUPPORT";
            levels.add(new LevelDto(type, price, mergedTimes.get(k)));
        }

// 6. Сортируем по цене (от высоких к низким)
        levels.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));

// 7. Формируем ответ
        Map<String, Object> result = new HashMap<>();
        result.put("pair", symbol);
        result.put("timeframe", timeframe);
        result.put("candlesAnalyzed", candles.size());
        result.put("currentPrice", livePrice);
        result.put("levelsCount", levels.size());
        result.put("levels", levels);

        return result;
    }
}