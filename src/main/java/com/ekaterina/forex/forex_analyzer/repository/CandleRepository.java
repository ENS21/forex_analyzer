package com.ekaterina.forex.forex_analyzer.repository;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    List<Candle> findBySymbolAndTimeframeOrderByOpenTimeAsc(String symbol, String timeframe);

    List<Candle> findTop100BySymbolAndTimeframeOrderByOpenTimeDesc(String symbol, String timeframe);

    Optional<Candle> findBySymbolAndTimeframeAndOpenTime(String symbol, String timeframe, LocalDateTime openTime);
}