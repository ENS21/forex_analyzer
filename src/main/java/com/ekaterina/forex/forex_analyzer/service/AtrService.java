package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AtrService {
    private final CandleRepository candleRepository;
    public AtrService(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /**
     * Вспомогательный класс (record) для хранения результатов расчета ATR.
     */
    public record AtrResult(double averageAtr, double minAtr, double maxAtr, int candlesCount) {
        @Override
        public String toString() {
            return String.format("Средний ATR: %.5f | Мин. ATR: %.5f | Макс. ATR: %.5f (Свечей: %d)",
                    averageAtr, minAtr, maxAtr, candlesCount);
        }
    }

    /**
     * Считает ATR за последние 22 закрытые дневные свечи (D1).
     *
     * @param symbol Валютная пара (например, "GBPUSD")
     * @return AtrResult с тремя значениями или null, если в базе недостаточно свечей.
     */
    public AtrResult calculateMonthlyAtr(String symbol) {
        // 1. Запрашиваем из базы 100 последних свечей D1 (используем твой метод)
        List<Candle> candles = candleRepository.findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, "D1");

        // 2. Проверка: если свечей меньше 22, считать нечего
        if (candles == null || candles.size() < 22) {
            return null;
        }

        double sumAtr = 0.0;
        double minAtr = Double.MAX_VALUE;
        double maxAtr = -Double.MAX_VALUE;
        int count = 0;

        // 3. Берем только первые 22 свечи (они самые новые, так как сортировка по убыванию)
        for (int i = 0; i < 22; i++) {
            Candle candle = candles.get(i);

            // Считаем ATR для одной свечи: Максимум минус Минимум
            // Конвертируем BigDecimal в double
            double high = candle.getHigh().doubleValue();
            double low = candle.getLow().doubleValue();

            double currentAtr = high - low;

            // Прибавляем к общей сумме
            sumAtr += currentAtr;

            // Ищем минимальный ATR
            if (currentAtr < minAtr) {
                minAtr = currentAtr;
            }
            // Ищем максимальный ATR
            if (currentAtr > maxAtr) {
                maxAtr = currentAtr;
            }

            count++;
        }

        // 4. Считаем средний ATR (сумма делить на 22)
        double averageAtr = sumAtr / 22.0;

        // 5. Возвращаем результат
        return new AtrResult(averageAtr, minAtr, maxAtr, count);
    }
}