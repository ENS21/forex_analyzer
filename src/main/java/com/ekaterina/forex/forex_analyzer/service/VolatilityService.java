package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class VolatilityService {

    private final CandleRepository candleRepository;

    public VolatilityService(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /** Результат статистики за один год.      */
    public record YearlyStats(int year, double maxPrice, double minPrice, double atr, double avgMonthlyAtr) {}

    /** Рассчитывает статистику за конкретный год (с 1 января по 31 декабря).      */
    public YearlyStats calculateYearlyStats(String symbol, int year) {
        LocalDateTime start = LocalDateTime.of(LocalDate.of(year, 1, 1), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.of(year, 12, 31), LocalTime.MAX);

        // Берем дневные свечи (D1) для расчета годовых экстремумов
        List<Candle> candles = candleRepository.findBySymbolAndTimeframeAndOpenTimeBetweenOrderByOpenTimeAsc(
                symbol, "D1", start, end);

        if (candles.isEmpty()) {
            return null; // Нет данных за этот год
        }

        BigDecimal maxHigh = candles.stream()
                .map(Candle::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal minLow = candles.stream()
                .map(Candle::getLow)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // Годовой ATR = Максимум года - Минимум года
        double yearlyAtr = maxHigh.subtract(minLow).doubleValue();

        // Средний месячный ATR = Годовой ATR / 12
        double avgMonthlyAtr = yearlyAtr / 12.0;

        return new YearlyStats(
                year,
                maxHigh.setScale(5, RoundingMode.HALF_UP).doubleValue(),
                minLow.setScale(5, RoundingMode.HALF_UP).doubleValue(),
                BigDecimal.valueOf(yearlyAtr).setScale(5, RoundingMode.HALF_UP).doubleValue(),
                BigDecimal.valueOf(avgMonthlyAtr).setScale(5, RoundingMode.HALF_UP).doubleValue()
        );
    }

    /** Находит самый волатильный и самый спокойный год в истории.      */
    public void printHistoryAnalysis(String symbol, int startYear, int endYear) {
        YearlyStats maxAtrYear = null;
        YearlyStats minAtrYear = null;

        for (int year = startYear; year <= endYear; year++) {
            YearlyStats stats = calculateYearlyStats(symbol, year);
            if (stats == null) continue;

            System.out.printf("Год: %d | Макс: %.5f | Мин: %.5f | ATR Года: %.5f | Ср. Мес: %.5f%n",
                    stats.year(), stats.maxPrice(), stats.minPrice(), stats.atr(), stats.avgMonthlyAtr());

            if (maxAtrYear == null || stats.atr() > maxAtrYear.atr()) {
                maxAtrYear = stats;
            }
            if (minAtrYear == null || stats.atr() < minAtrYear.atr()) {
                minAtrYear = stats;
            }
        }

        if (maxAtrYear != null && minAtrYear != null) {
            System.out.println("\n=== ИТОГИ ===");
            System.out.printf("🔥 Самое сильное движение было в %d году (ATR: %.5f)%n", maxAtrYear.year(), maxAtrYear.atr());
            System.out.printf("❄️ Самое слабое движение было в %d году (ATR: %.5f)%n", minAtrYear.year(), minAtrYear.atr());
        }
    }
}