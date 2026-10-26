package com.ekaterina.forex.forex_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Компонент для получения тиков (bid/ask) в реальном времени от терминала MetaTrader 5.
 * <p>
 * Работает по протоколу ZeroMQ (сокет типа SUB). При запуске приложения
 * создает фоновый поток (демон), который бесконечно слушает порт 5555
 * и передает входящие данные в {@link TickCache}.
 * </p>
 */
@Component
public class MT5TickSubscriber {
    private static final Logger log = LoggerFactory.getLogger(MT5TickSubscriber.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final TickCache tickCache;
    private final AlertService alertService;

    /**
     * Конструктор для внедрения зависимости кэша тиков.
     *
     * @param tickCache Кэш для сохранения актуальных рыночных данных.
     */
    public MT5TickSubscriber(TickCache tickCache, AlertService alertService) {
        this.alertService = alertService;
        this.tickCache = tickCache;
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
        Thread thread = new Thread(this::listen, "mt5-tick-subscriber");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Основной цикл прослушивания сокета.
     * <p>
     * Подключается к локальному порту 5555, подписывается на топик "tick".
     * Блокируется на методе {@code recvStr()}, ожидая новые сообщения,
     * и передает их на обработку в {@code processMessage()}.
     * </p>
     */
    private void listen() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://localhost:5555");
            subscriber.subscribe("tick".getBytes());
            log.info("Подключился к MT5 ZeroMQ на порту 5555");

            while (!Thread.currentThread().isInterrupted()) {
                String message = subscriber.recvStr();
                if (message != null) {
                    processMessage(message);
                }
            }
        }
    }

    /** Парсит JSON тика и обновляет TickCache. */
    private void processMessage(String message) {
        try {
            int spaceIdx = message.indexOf(' ');
            if (spaceIdx < 0) return;
            String json = message.substring(spaceIdx + 1);

            JsonNode node = mapper.readTree(json);
            String symbol = node.get("symbol").asText();
            double bid = node.get("bid").asDouble();
            double ask = node.get("ask").asDouble();
            long timeUnix = node.get("time").asLong();
            double dayOpen = node.has("dayOpen") ? node.get("dayOpen").asDouble() : 0.0;

            // Извлекаем RSI для всех трех таймфреймов (безопасно, если их нет — будет null)
            Double rsiD1 = node.has("rsiD1") ? node.get("rsiD1").asDouble() : null;
            Double rsiH4 = node.has("rsiH4") ? node.get("rsiH4").asDouble() : null;
            Double rsiH1 = node.has("rsiH1") ? node.get("rsiH1").asDouble() : null; // НОВОЕ

            // Передаем данные в кэш (добавили rsiH1)
            tickCache.update(symbol, bid, ask, timeUnix, dayOpen, rsiD1, rsiH4, rsiH1);

            // Проверяем, не пора ли отправить сигнал в MAX
            alertService.checkAndSendSignals(symbol);

        } catch (Exception e) {
            log.error("Ошибка обработки тика: {}", message, e);
        }
    }
}