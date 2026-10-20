package com.ekaterina.forex.forex_analyzer.controller;

import com.ekaterina.forex.forex_analyzer.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * REST-контроллер для предоставления аналитических данных по валютным парам.
 * <p>
 * Принимает HTTP GET-запросы и делегирует вычисления в {@link AnalyticsService}.
 * </p>
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Аналитика валютных пар: RSI, сигналы перекупленности/перепроданности")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    /**
     * Конструктор для внедрения сервиса аналитики.
     * @param analyticsService Сервис, содержащий бизнес-логику.
     */
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }
    /**
     * Эндпоинт для получения уровней поддержки и сопротивления.
     *
     * @param symbol Валютная пара (например, EURUSD).
     * @param timeframe Таймфрейм графика (например, D1 или H4).
     * @return JSON с найденными уровнями или объект ошибки.
     */
    @GetMapping(value = "/levels/{symbol}/{timeframe}", produces = "application/json;charset=UTF-8")
    @Operation(
            summary = "Уровни поддержки и сопротивления",
            description = "Возвращает список локальных уровней для указанной пары и таймфрейма"
    )
    public Object getLevels(
            @Parameter(description = "Валютная пара, например EURUSD" ) @PathVariable String symbol,
            @Parameter(description = "Таймфрейм, например D1 или H4" ) @PathVariable String timeframe) {
        return analyticsService.findLevels(symbol, timeframe);
    }

    /**
     * Эндпоинт для получения комплексного анализа (RSI, цена, тренд, сигналы).
     *
     * @param symbol Валютная пара (например, EURUSD).
     * @param timeframe Таймфрейм графика (например, D1 или H4).
     * @return JSON с результатами анализа или объект ошибки.
     */
    @GetMapping(value = "/{symbol}/{timeframe}", produces = "application/json;charset=UTF-8")
    @Operation(
            summary = "Анализ валютной пары",
            description = "Возвращает RSI, текущую цену и торговый сигнал для указанной пары и таймфрейма"
    )
    public Object getAnalytics(
            @Parameter( description = "Валютная пара, например EURUSD" ) @PathVariable String symbol,
            @Parameter(description = "Таймфрейм, например D1 или H4" ) @PathVariable String timeframe) {
        return analyticsService.getRsiAnalysis(symbol, timeframe);
    }
}