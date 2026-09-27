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

@Service
public class AnalyticsService {
    private final CandleRepository candleRepository;
    public AnalyticsService(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }
    /**
     * Считает RSI по свечам из базы данных.
     */
    public Map<String, Object> getRsiAnalysis(String symbol, String timeframe) {
        List<Candle> candles = candleRepository
                .findBySymbolAndTimeframeOrderByOpenTimeAsc(symbol, timeframe);

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
        result.put("currentPrice", lastCandle.getClose());
        result.put("rsi", Math.round(rsiValue * 100.0) / 100.0);
        result.put("signal",
                rsiValue > 70 ? "ПЕРЕКУПЛЕНА"
                        : rsiValue < 30 ? "ПЕРЕПРОДАНА"
                        : "НЕЙТРАЛЬНО");
        return result;
    }
}