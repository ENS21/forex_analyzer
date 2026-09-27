package com.ekaterina.forex.forex_analyzer.controller;

import com.ekaterina.forex.forex_analyzer.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{symbol}/{timeframe}")
    public Map<String, Object> getAnalytics(
            @PathVariable String symbol,
            @PathVariable String timeframe) {
        return analyticsService.getRsiAnalysis(symbol, timeframe);
    }
}