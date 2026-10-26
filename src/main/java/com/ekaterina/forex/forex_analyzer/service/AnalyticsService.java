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

/**
 * Сервис для технического анализа рыночных данных.
 * Предоставляет методы для расчета индикатора RSI, определения тренда
 * и поиска уровней поддержки/сопротивления на основе исторических свечей.
 */
@Service
public class AnalyticsService {
    private final TickCache tickCache;
    private final CandleRepository candleRepository;

    /**
     * Конструктор для внедрения зависимостей.
     *
     * @param candleRepository Репозиторий для доступа к историческим свечам.
     * @param tickCache Кэш для получения текущих ("живых") цен.
     */
    public AnalyticsService(CandleRepository candleRepository, TickCache tickCache) {
        this.tickCache = tickCache;
        this.candleRepository = candleRepository;
    }

    /**
     * Выполняет комплексный анализ валютной пары.
     * Рассчитывает RSI, текущее изменение цены, тренд (по MA20/MA50)
     * и находит ближайшие уровни поддержки и сопротивления.
     *
     * @param symbol Валютная пара (например, "EURUSD").
     * @param timeframe Таймфрейм (например, "D1", "H4").
     * @return {@link AnalysisResponse} с результатами анализа,
     *         либо {@link ErrorResponse}, если данных недостаточно.
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

        //----РАСЧЕТЫ(выносим в переменные, чтобы не писать все в Builder
        // --- ПРОВЕРКА НА REAL-TIME RSI ИЗ КЭША ---

        Double realTimeRsi = null;

        // Сохраняем последний тик в переменную, чтобы не дергать кэш дважды
        TickCache.Tick lastTick = tickCache.getLast(symbol);

        if (lastTick != null) {
            if ("D1".equalsIgnoreCase(timeframe)) {
                realTimeRsi = lastTick.getRsiD1();
            } else if ("H4".equalsIgnoreCase(timeframe)) {
                realTimeRsi = lastTick.getRsiH4();
            } else if ("H1".equalsIgnoreCase(timeframe)) {
                realTimeRsi = lastTick.getRsiH1();
            }
        }
        // Если real-time RSI пришел и он больше 0, берем его. Иначе считаем сами.
        double finalRsi = (realTimeRsi != null && realTimeRsi > 0) ? realTimeRsi : calcRsi(series);

        Double dayHigh = tickCache.getDayHigh(symbol);
        Double dayLow = tickCache.getDayLow(symbol);

        double finalDayHigh = round(dayHigh != null ? dayHigh : lastCandle.getHigh().doubleValue(), 5);
        double finalDayLow = (round(dayLow != null ? dayLow : lastCandle.getLow().doubleValue(), 5));


        String trend = calcTrend(candles);

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

        // --- СБОРКА ОТВЕТА ЧЕРЕЗ BUILDER ---
        return AnalysisResponse.builder()
                .pair(symbol)
                .timeframe(timeframe)
                .candlesCount(candles.size())
                .lastCandleTime(lastCandle.getOpenTime().toString())
                .currentPrice(round(livePrice, 5))
                .openPrice(round(openPrice, 5))
                .change(round(change, 5))
                .changePercent(round(changePercent, 2))
                .rsi(round(finalRsi, 2))
                .signal(signalFromRsi(finalRsi))
                .dayHigh(finalDayHigh)
                .dayLow(finalDayLow)
                .trend(trend)
                .nearestSupport(nearestSup != null ? round(nearestSup, 5) : null)
                .nearestResistance(nearestRes != null ? round(nearestRes, 5) : null)
                .build();
    }

    /**
     * Находит и классифицирует уровни поддержки и сопротивления.
     * Использует алгоритм поиска локальных экстремумов с последующей группировкой.
     *
     * @param symbol Валютная пара.
     * @param timeframe Таймфрейм.
     * @return {@link LevelsResponse} со списком уровней,
     *         либо {@link ErrorResponse} при нехватке данных.
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
    /**
     * Загружает последние 100 свечей из базы данных для указанной пары и таймфрейма.
     * <p>
     * Данные извлекаются в порядке "от новых к старым", после чего список
     * переворачивается (reverse), чтобы обеспечить хронологический порядок
     * (от старых к новым), который требуется для библиотеки TA4J.
     * </p>
     *
     * @param symbol    Название валютной пары.
     * @param timeframe Таймфрейм (например, "D1", "H4").
     * @return Список свечей в хронологическом порядке.
     */
    private List<Candle> loadCandles(String symbol, String timeframe) {
        List<Candle> candles = candleRepository
                .findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(symbol, timeframe);
        Collections.reverse(candles);
        return candles;
    }

    /**
     * Формирует объект {@link BarSeries} (серию баров) для библиотеки TA4J
     * на основе списка сущностей {@link Candle} из базы данных.
     * <p>
     * Метод выполняет следующие важные шаги:
     * <ul>
     *     <li>Определяет корректную длительность бара (Duration) в зависимости от таймфрейма.</li>
     *     <li>Отбрасывает свечи с отсутствующим временем (null).</li>
     *     <li>Защищает TA4J от падения (IllegalArgumentException), пропуская дубликаты
     *         или свечи с нарушенным временным порядком.</li>
     * </ul>
     * </p>
     *
     * @param symbol    Название валютной пары.
     * @param timeframe Таймфрейм.
     * @param candles   Список свечей (от старых к новым).
     * @return Готовая серия баров для расчетов индикаторов.
     */
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

    /**
     * Рассчитывает значение индикатора RSI (Relative Strength Index)
     * для последней свечи в серии, используя стандартный период 14.
     *
     * @param series Серия баров (свечей), подготовленная для библиотеки TA4J.
     * @return Значение RSI для текущей (последней) свечи.
     */
    private double calcRsi(BarSeries series) {
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), 14);
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }

    /**
     * Преобразует числовое значение RSI в текстовый торговый сигнал.
     * <ul>
     *     <li>Больше 70 — зона перекупленности (сигнал к продаже).</li>
     *     <li>Меньше 30 — зона перепроданности (сигнал к покупке).</li>
     *     <li>В остальных случаях — нейтральная зона.</li>
     * </ul>
     *
     * @param rsi Числовое значение индикатора RSI.
     * @return Строка с сигналом ("ПЕРЕКУПЛЕНА", "ПЕРЕПРОДАНА" или "НЕЙТРАЛЬНО").
     */
    private String signalFromRsi(double rsi) {
        if (rsi > 70) return "ПЕРЕКУПЛЕНА";
        if (rsi < 30) return "ПЕРЕПРОДАНА";
        return "НЕЙТРАЛЬНО";
    }

    /**
     * Получает текущую "живую" цену (Bid) из кэша тиков.
     * Если данных в кэше нет (например, MT5 отключен или это выходной день),
     * возвращается цена закрытия последней сохраненной свечи.
     *
     * @param symbol     Название валютной пары.
     * @param lastCandle Последняя свеча из базы данных (используется как fallback).
     * @return Актуальная цена.
     */
    private double getLivePrice(String symbol, Candle lastCandle) {
        Double live = tickCache.getCurrentPrice(symbol);
        return live != null ? live : lastCandle.getClose().doubleValue();
    }

    /**
     * Получает цену открытия текущего торгового дня.
     * Приоритет отдается данным из кэша реального времени (от MT5).
     * Если данные отсутствуют или равны нулю, происходит возврат (fallback)
     * к цене открытия последней D1-свечи или её цене закрытия.
     *
     * @param symbol     Название валютной пары.
     * @param lastCandle Последняя свеча из базы данных.
     * @return Цена открытия дня.
     */
    private double getOpenPrice(String symbol, Candle lastCandle) {
        Double open = tickCache.getOpenPrice(symbol);
        if (open != null && open > 0) {
            return open;
        }
        // Fallback на open последней D1-свечи
        double candleOpen = lastCandle.getOpen().doubleValue();
        return candleOpen > 0 ? candleOpen : lastCandle.getClose().doubleValue();
    }
    /**
     * Ищет локальные максимумы и минимумы на графике.
     * Свеча считается экстремумом, если её цена (high/low) выше/ниже,
     * чем у N свечей слева и справа (параметр lookback).
     *
     * @param candles Список свечей, отсортированный от старых к новым.
     * @return Список найденных точек разворота (LevelPoint).
     */
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

    /**
     * Группирует близко расположенные ценовые уровни в один, чтобы убрать "шум".
     * <p>
     * Если разница между ценами двух уровней меньше заданного допуска (tolerance),
     * второй уровень считается дубликатом и игнорируется.
     * </p>
     *
     * @param input     Список "сырых" найденных уровней.
     * @param tolerance Допустимая разница в процентах (например, 0.0015 = 0.15%).
     * @return Отфильтрованный список уникальных уровней.
     */
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

    /**
     * Округляет число до указанного количества знаков после запятой.
     * Используется для форматирования цен перед отправкой клиенту.
     *
     * @param value  Исходное число.
     * @param digits Количество знаков после запятой.
     * @return Округленное значение.
     */
    private double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }

    /**
     * Вспомогательный класс для временного хранения точки разворота (уровня).
     * Содержит цену уровня и время его формирования.
     */
    private static class LevelPoint {
        final double price;
        final String time;
        LevelPoint(double price, String time) {
            this.price = price;
            this.time = time;
        }
    }

    /**
     * Определяет текущий тренд на основе пересечения скользящих средних (MA20 и MA50).
     * <p>
     * Если MA20 выше MA50 более чем на 0.1%, тренд считается восходящим.
     * Если ниже — нисходящим. Иначе тренд боковой (флэт).
     * Для расчета требуется минимум 50 свечей.
     * </p>
     *
     * @param candles Список свечей (от старых к новым).
     * @return Строка с описанием тренда: "ВОСХОДЯЩИЙ", "НИСХОДЯЩИЙ", "БОКОВОЙ" или "НЕДОСТАТОЧНО ДАННЫХ".
     */
    private String calcTrend(List<Candle> candles) {
        if (candles.size() < 50) return "НЕДОСТАТОЧНО ДАННЫХ";

        double ma20 = calcMA(candles, 20);
        double ma50 = calcMA(candles, 50);

        if (ma50 == 0.0) {
            return "БОКОВОЙ";
        }

        double diffPercent = (ma20 - ma50) / ma50 * 100.0;

        if (diffPercent > 0.1) return "ВОСХОДЯЩИЙ";
        if (diffPercent < -0.1) return "НИСХОДЯЩИЙ";
        return "БОКОВОЙ";
    }

    /**
     * Рассчитывает простое скользящее среднее (SMA) для цен закрытия (Close).
     *
     * @param candles Список свечей.
     * @param period  Период скользящей средней (например, 20 или 50).
     * @return Среднее значение цен закрытия за указанный период.
     */
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
