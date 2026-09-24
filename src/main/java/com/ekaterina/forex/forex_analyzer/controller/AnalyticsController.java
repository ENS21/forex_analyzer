package com.ekaterina.forex.forex_analyzer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")

public class AnalyticsController {
    @GetMapping("/analytics/EURUSD")
    public Map<String, Object> getAnalytics() {
        double[] prices = {1.085, 1.087, 1.086, 1.089, 1.091, 1.090, 1.093, 1.095, 1.094, 1.096,
                1.098, 1.097, 1.099, 1.101, 1.100, 1.102, 1.104, 1.103, 1.105, 1.107};

        BarSeries series = new BaseBarSeriesBuilder()
                .withName("EURUSD")
                .withNumTypeOf(DoubleNum.class)
                .build();
        for (int i = 0; i < prices.length; i++) {
            series.addBar(new BaseBar(
                    Duration.ofHours(1),
                    ZonedDateTime.now().minusHours(prices.length - i),
                    prices[i],   // open
                    prices[i],   // high
                    prices[i],   // low
                    prices[i],   // close
                    0.0          // volume
            ));
        }


        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 14);
        double rsiValue = rsi.getValue(series.getEndIndex()).doubleValue();

        Map<String, Object> result = new HashMap<>();
        result.put("pair", "EURUSD");
        result.put("currentPrice", prices[prices.length - 1]);
        result.put("rsi", Math.round(rsiValue * 100.0) / 100.0);
        result.put("signal", rsiValue > 70 ? "ПЕРЕКУПЛЕНА" : rsiValue < 30 ? "ПЕРЕПРОДАНА" : "НЕЙТРАЛЬНО");
        return result;
    }
}