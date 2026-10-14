package com.ekaterina.forex.forex_analyzer.controller;

import com.ekaterina.forex.forex_analyzer.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Аналитика валютных пар: RSI, сигналы перекупленности/перепроданности")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    @GetMapping(value = "/levels/{symbol}/{timeframe}", produces = "application/json;charset=UTF-8")
    public Object getLevels(
            @PathVariable String symbol,
            @PathVariable String timeframe) {
        return analyticsService.findLevels(symbol, timeframe);
    }
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping(value = "/{symbol}/{timeframe}", produces = "application/json;charset=UTF-8")
    @Operation(summary = "Анализ валютной пары",
    description = "Возвращает RSI, текущую цену и торговый сигнал для указанной пары и таймфрейма")
    public Object getAnalytics(
            @PathVariable String symbol,
            @PathVariable String timeframe) {
        return analyticsService.getRsiAnalysis(symbol, timeframe);
    }
}