package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.dto.AnalysisResponse;
import com.ekaterina.forex.forex_analyzer.dto.ErrorResponse;
import com.ekaterina.forex.forex_analyzer.dto.LevelDto;
import com.ekaterina.forex.forex_analyzer.dto.LevelsResponse;
import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AnalyticsService {
    private final TickCache tickCache;
    private final CandleRepository candleRepository;

    public AnalyticsService(CandleRepository candleRepository, TickCache tickCache) {
        this.tickCache = tickCache;
        this.candleRepository = candleRepository;
    }

    /**
     * RSI + цена + изменение дня + сигнал.
     */
    public Object getRsiAnalysis(String symbol, String timeframe) {
        List<Candle> candles = loadCandles(symbol, timeframe);

        if (candles.size() < 15) {
            return new ErrorResponse("Недостаточно свечей для расчёта RSI (нужно минимум 15)", candles.size());
        }

        BarSeries series = buildSeries(symbol, timeframe, candles);
        double rsiValue = calcRsi(series);

        Candle lastCandle = candles.get(candles.size() - 1);
        double livePrice = getLivePrice(symbol, lastCandle);
        double openPrice = getOpenPrice(symbol, lastCandle);
        double change = livePrice - openPrice;
        double changePercent = openPrice != 0 ? (change / openPrice) * 100.0 : 0.0;


        AnalysisResponse r = new AnalysisResponse();
        r.setPair(symbol);
        r.setTimeframe(timeframe);
        r.setCandlesCount(candles.size());
        r.setLastCandleTime(lastCandle.getOpenTime().toString());
        r.setCurrentPrice(round(livePrice, 5));
        r.setOpenPrice(round(openPrice, 5));
        r.setChange(round(change, 5));
        r.setChangePercent(round(changePercent, 2));
        r.setRsi(round(rsiValue, 2));
        r.setSignal(signalFromRsi(rsiValue));

        // dayHigh / dayLow
        Double dayHigh = tickCache.getDayHigh(symbol);
        Double dayLow = tickCache.getDayLow(symbol);
        r.setDayHigh(round(dayHigh != null ? dayHigh : lastCandle.getHigh().doubleValue(), 5));
        r.setDayLow(round(dayLow != null ? dayLow : lastCandle.getLow().doubleValue(), 5));

        // trend (по MA20 / MA50)
        r.setTrend(calcTrend(candles));

        // ближайшие уровни
        List<LevelPoint> extremes = findLocalExtremes(candles);
        List<LevelPoint> merged = mergeSimilar(extremes, 0.0015);

        Double nearestSup = null;
        Double nearestRes = null;
        for (LevelPoint p : merged) {
            if (p.price < livePrice) {
                if (nearestSup == null || p.price > nearestSup) nearestSup = p.price;
            } else if (p.price > livePrice) {
                if (nearestRes == null || p.price < nearestRes) nearestRes = p.price;
            }
        }
        // Fallback: если support не найден — берём минимум за период
        if (nearestSup == null) {
            nearestSup = candles.stream()
                    .mapToDouble(c -> c.getLow().doubleValue())
                    .min()
                    .orElse(livePrice);
        }
// Fallback: если resistance не найден — берём максимум за период
        if (nearestRes == null) {
            nearestRes = candles.stream()
                    .mapToDouble(c -> c.getHigh().doubleValue())
                    .max()
                    .orElse(livePrice);
        }
        r.setNearestSupport(nearestSup != null ? round(nearestSup, 5) : null);
        r.setNearestResistance(nearestRes != null ? round(nearestRes, 5) : null);

        return r;
    }

    /**
     * Уровни поддержки/сопротивления.
     */
    public Object findLevels(String symbol, String timeframe) {
        List<Candle> candles = loadCandles(symbol, timeframe);

        if (candles.size() < 20) {
            return new ErrorResponse("Недостаточно свечей для анализа уровней", candles.size());
        }

        List<LevelPoint> extremes = findLocalExtremes(candles);
        List<LevelPoint> merged = mergeSimilar(extremes, 0.0015);

        Candle lastCandle = candles.get(candles.size() - 1);
        double livePrice = getLivePrice(symbol, lastCandle);

        List<LevelDto> levels = new ArrayList<>();
        for (LevelPoint p : merged) {
            String type = p.price > livePrice ? "RESISTANCE" : "SUPPORT";
            levels.add(new LevelDto(type, p.price, p.time));
        }
        levels.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));

        LevelsResponse r = new LevelsResponse();
        r.setPair(symbol);
        r.setTimeframe(timeframe);
        r.setCandlesAnalyzed(candles.size());
        r.setCurrentPrice(round(livePrice, 5));
        r.setLevelsCount(levels.size());
        r.setLevels(levels);
        return r;
    }

    // ================== приватные помощники ==================

    private List<Candle> loadCandles(String symbol, String timeframe) {
        List<Candle> candles = candleRepository
                .findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, timeframe);
        Collections.reverse(candles);
        return candles;
    }

    private BarSeries buildSeries(String symbol, String timeframe, List<Candle> candles) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol + "_" + timeframe)
                .withNumTypeOf(DoubleNum.class)
                .build();

        // Получаем правильную длительность для этого таймфрейма
        Duration barDuration = getDurationForTimeframe(timeframe);

        for (Candle c : candles) {
            if (c.getOpenTime() == null) continue; // Защита от битых данных

            ZonedDateTime endTime = c.getOpenTime().atZone(ZoneId.systemDefault());

            // ЗАЩИТА ОТ ОШИБКИ TA4J:
            // Если серия не пуста, проверяем, что время конца новой свечи строго больше предыдущей
            if (!series.isEmpty() && !endTime.isAfter(series.getLastBar().getEndTime())) {
                continue; // Пропускаем дубликат или свечу с нарушенным порядком
            }

            series.addBar(new BaseBar(
                    barDuration, // <-- Используем вычисленную длительность
                    endTime,
                    c.getOpen().doubleValue(),
                    c.getHigh().doubleValue(),
                    c.getLow().doubleValue(),
                    c.getClose().doubleValue(),
                    c.getVolume() != null ? c.getVolume().doubleValue() : 0.0
            ));
        }
        return series;
    }

    private double calcRsi(BarSeries series) {
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 14);
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }

    private String signalFromRsi(double rsi) {
        if (rsi > 70) return "ПЕРЕКУПЛЕНА";
        if (rsi < 30) return "ПЕРЕПРОДАНА";
        return "НЕЙТРАЛЬНО";
    }

    private double getLivePrice(String symbol, Candle lastCandle) {
        Double live = tickCache.getCurrentPrice(symbol);
        return live != null ? live : lastCandle.getClose().doubleValue();
    }

    private double getOpenPrice(String symbol, Candle lastCandle) {
        Double open = tickCache.getOpenPrice(symbol);
        if (open != null && open > 0) {
            return open;
        }
        // Fallback на open последней D1-свечи
        double candleOpen = lastCandle.getOpen().doubleValue();
        return candleOpen > 0 ? candleOpen : lastCandle.getClose().doubleValue();
    }

    private List<LevelPoint> findLocalExtremes(List<Candle> candles) {
        int lookback = 3;
        List<LevelPoint> points = new ArrayList<>();

        for (int i = lookback; i < candles.size() - lookback; i++) {
            Candle c = candles.get(i);
            boolean isMax = true;
            boolean isMin = true;

            for (int j = 1; j <= lookback; j++) {
                if (candles.get(i - j).getHigh().doubleValue() >= c.getHigh().doubleValue()
                        || candles.get(i + j).getHigh().doubleValue() >= c.getHigh().doubleValue()) {
                    isMax = false;
                }
                if (candles.get(i - j).getLow().doubleValue() <= c.getLow().doubleValue()
                        || candles.get(i + j).getLow().doubleValue() <= c.getLow().doubleValue()) {
                    isMin = false;
                }
            }

            if (isMax) points.add(new LevelPoint(c.getHigh().doubleValue(), c.getOpenTime().toString()));
            if (isMin) points.add(new LevelPoint(c.getLow().doubleValue(), c.getOpenTime().toString()));
        }
        return points;
    }

    private List<LevelPoint> mergeSimilar(List<LevelPoint> input, double tolerance) {
        List<LevelPoint> merged = new ArrayList<>();
        for (LevelPoint p : input) {
            boolean found = false;
            for (LevelPoint ex : merged) {
                if (Math.abs(ex.price - p.price) / ex.price < tolerance) {
                    found = true;
                    break;
                }
            }
            if (!found) merged.add(p);
        }
        return merged;
    }

    private double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }

    private static class LevelPoint {
        final double price;
        final String time;
        LevelPoint(double price, String time) {
            this.price = price;
            this.time = time;
        }
    }
    private String calcTrend(List<Candle> candles) {
        if (candles.size() < 50) return "НЕДОСТАТОЧНО ДАННЫХ";

        double ma20 = calcMA(candles, 20);
        double ma50 = calcMA(candles, 50);

        if (ma50 == 0.0) {
            return "БОКОВОЙ";            //не помогло
        }

        double diffPercent = (ma20 - ma50) / ma50 * 100.0;

        if (diffPercent > 0.1) return "ВОСХОДЯЩИЙ";
        if (diffPercent < -0.1) return "НИСХОДЯЩИЙ";
        return "БОКОВОЙ";
    }

    private double calcMA(List<Candle> candles, int period) {
        int start = candles.size() - period;
        double sum = 0;
        for (int i = start; i < candles.size(); i++) {
            sum += candles.get(i).getClose().doubleValue();
        }
        return sum / period;
    }

    /**
     * Возвращает длительность одного бара в зависимости от таймфрейма.
     */
    private Duration getDurationForTimeframe(String timeframe) {
        if (timeframe == null) return Duration.ofDays(1);

        return switch (timeframe.toUpperCase()) {
            case "M1" -> Duration.ofMinutes(1);
            case "M5" -> Duration.ofMinutes(5);
            case "M15" -> Duration.ofMinutes(15);
            case "M30" -> Duration.ofMinutes(30);
            case "H1" -> Duration.ofHours(1);
            case "H4" -> Duration.ofHours(4);
            case "D1" -> Duration.ofDays(1);
            case "W1" -> Duration.ofDays(7);
            case "MN", "MN1" -> Duration.ofDays(30); // Примерно для месячного
            default -> Duration.ofDays(1); // Значение по умолчанию, если пришел неизвестный таймфрейм
        };
    }
}
