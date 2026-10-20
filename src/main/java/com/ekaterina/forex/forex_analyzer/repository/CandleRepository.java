package com.ekaterina.forex.forex_analyzer.repository;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;

/**
 * Репозиторий Spring Data JPA для управления сущностями {@link Candle}.
 * Предоставляет методы для извлечения исторических данных (свечей)
 * из базы данных по валютной паре и таймфрейму.
 */
@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    /**
     * Находит все свечи для указанной пары и таймфрейма,
     * отсортированные по времени от старых к новым (хронологический порядок).
     *
     * @param symbol    Название валютной пары (например, "EURUSD").
     * @param timeframe Таймфрейм (например, "D1", "H4").
     * @return Список свечей в хронологическом порядке.
     */
    List<Candle> findBySymbolAndTimeframeOrderByOpenTimeAsc(String symbol, String timeframe);

    /**
     * Находит последние 100 свечей для указанной пары и таймфрейма,
     * отсортированные по времени от новых к старым.
     * Используется для быстрого доступа к актуальным рыночным данным.
     *
     * @param symbol    Название валютной пары.
     * @param timeframe Таймфрейм.
     * @return Список из 100 последних свечей (сначала самые новые).
     */
    List<Candle> findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(String symbol, String timeframe);

    /**
     * Находит одну конкретную свечу по её уникальному времени открытия.
     * Используется в фоновых задачах (например, при обновлении текущей свечи из MT5),
     * чтобы проверить, существует ли она уже в базе.
     *
     * @param symbol    Название валютной пары.
     * @param timeframe Таймфрейм.
     * @param openTime  Точное время открытия свечи.
     * @return {@link Optional} с найденной свечой, или пустой Optional, если такой свечи нет.
     */
    Optional<Candle> findBySymbolAndTimeframeAndOpenTime(String symbol, String timeframe, LocalDateTime openTime);
}