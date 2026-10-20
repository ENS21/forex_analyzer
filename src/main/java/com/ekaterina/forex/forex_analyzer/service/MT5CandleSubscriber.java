package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.time.ZoneOffset;
/**
 * Компонент для получения данных о свечах в реальном времени от терминала MetaTrader 5.
 * <p>
 * Работает по протоколу ZeroMQ (сокет типа SUB). При запуске приложения
 * создает фоновый поток (демон), который бесконечно слушает порт 5555
 * и обрабатывает входящие JSON-сообщения, сохраняя их в базу данных.
 * </p>
 */
@Component
public class MT5CandleSubscriber {
    private static final Logger log = LoggerFactory.getLogger(MT5CandleSubscriber.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final CandleRepository candleRepository;

    /**
     * Конструктор для внедрения зависимости репозитория.
     *
     * @param candleRepository Репозиторий для сохранения и обновления свечей в БД.
     */
    public MT5CandleSubscriber(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /**
     * Инициализирует и запускает фоновый поток для прослушивания ZeroMQ.
     * <p>
     * Аннотация {@link PostConstruct} гарантирует, что этот метод будет вызван
     * автоматически сразу после создания бина Spring. Поток помечается как
     * daemon, чтобы он завершился вместе с основным приложением.
     * </p>
     */
    @PostConstruct
    public void start() {
        Thread thread = new Thread(this::listen, "mt5-candle-subscriber");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Основной цикл прослушивания сокета.
     * <p>
     * Подключается к локальному порту 5555, подписывается на топик "candle".
     * Блокируется на методе {@code recvStr()}, ожидая новые сообщения.
     * </p>
     */
    private void listen() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://localhost:5555");
            subscriber.subscribe("candle".getBytes());
            log.info("Подключился к MT5 ZeroMQ на порту 5555");

            while (!Thread.currentThread().isInterrupted()) {
                String message = subscriber.recvStr();
                if (message != null) {
                    processMessage(message);
                }
            }
        }
    }
    /**
     * Обрабатывает одно входящее сообщение от MT5.
     * <p>
     * Метод разбирает строку (отсекая префикс топика), парсит JSON,
     * извлекает данные о свече и выполняет операцию "Upsert" (Update or Insert):
     * если свеча с таким временем уже есть в БД, она обновляется,
     * иначе — создается новая.
     * </p>
     *
     * @param message Сырая строка от MT5 в формате: "candle {json_data}"
     */

    private void processMessage(String message) {
        try {
            int spaceIdx = message.indexOf(' ');
            if (spaceIdx < 0) return;
            String json = message.substring(spaceIdx + 1);

            JsonNode node = mapper.readTree(json);
            String symbol = node.get("symbol").asText();
            String timeframe = node.get("timeframe").asText();
            long openTimeUnix = node.get("openTime").asLong();

            BigDecimal open = new BigDecimal(node.get("open").asText());
            BigDecimal high = new BigDecimal(node.get("high").asText());
            BigDecimal low = new BigDecimal(node.get("low").asText());
            BigDecimal close = new BigDecimal(node.get("close").asText());
            BigDecimal volume = new BigDecimal(node.get("volume").asText());

            // MT5 отдаёт серверное время брокера — сохраняем как есть
            LocalDateTime openTime = LocalDateTime.ofEpochSecond(
                    openTimeUnix, 0, ZoneOffset.UTC
            );

            Optional<Candle> existing = candleRepository
                    .findBySymbolAndTimeframeAndOpenTime(symbol, timeframe, openTime);

            Candle candle;
            if (existing.isPresent()) {
                candle = existing.get();
                candle.setOpen(open);
                candle.setHigh(high);
                candle.setLow(low);
                candle.setClose(close);
                candle.setVolume(volume);
                log.info("Обновлена свеча {} {} {}", symbol, timeframe, openTime);
            } else {
                candle = new Candle();
                candle.setSymbol(symbol);
                candle.setTimeframe(timeframe);
                candle.setOpenTime(openTime);
                candle.setOpen(open);
                candle.setHigh(high);
                candle.setLow(low);
                candle.setClose(close);
                candle.setVolume(volume);
                log.info("Добавлена новая свеча {} {} {}", symbol, timeframe, openTime);
            }

            candleRepository.save(candle);

        } catch (Exception e) {
            log.error("Ошибка обработки свечи: {}", message, e);
        }
    }
}