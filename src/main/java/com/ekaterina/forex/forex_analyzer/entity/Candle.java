package com.ekaterina.forex.forex_analyzer.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Сущность, представляющая одну свечу (бар) на графике в базе данных.
 * Хранит исторические и текущие данные OHLCV (Open, High, Low, Close, Volume)
 * для конкретной валютной пары и таймфрейма.
 */
@Entity
@Table (
        name = "candles",
        indexes = {
        // Составной индекс для быстрого поиска свечей по паре, таймфрейму и времени
        @Index(name = "idx_symbol_timeframe_time", columnList = "symbol,timeframe,open_time")
        }
        )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Candle {
    /** Уникальный идентификатор записи (автоинкремент). */
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название валютной пары (например, "EURUSD"). */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Таймфрейм свечи (например, "M1", "H4", "D1"). */
    @Column(nullable = false, length = 10)
    private String timeframe;

    /** Время открытия свечи (хранится как open_time в БД). */
    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;

    /**
     * Цена открытия.
     * precision = 18, scale = 8 задают точность хранения (до 8 знаков после запятой),
     * что критически важно для валютных котировок.
     */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal open;

    /** Максимальная цена за период. */

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal high;

    /** Минимальная цена за период. */

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal low;

    /** Цена закрытия. */
    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal close;

    /** Объем торгов (может отсутствовать у некоторых брокеров, поэтому nullable). */
    @Column(precision = 18, scale = 8)
    private BigDecimal volume;
}