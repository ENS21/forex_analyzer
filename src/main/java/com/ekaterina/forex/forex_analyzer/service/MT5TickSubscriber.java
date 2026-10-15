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

@Component
public class MT5TickSubscriber {
    private static final Logger log = LoggerFactory.getLogger(MT5TickSubscriber.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final TickCache tickCache;

    public MT5TickSubscriber(TickCache tickCache) {
        this.tickCache = tickCache;
    }

    @PostConstruct
    public void start() {
        Thread thread = new Thread(this::listen, "mt5-tick-subscriber");
        thread.setDaemon(true);
        thread.start();
    }

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

            tickCache.update(symbol, bid, ask, timeUnix, dayOpen);
            log.info("Тик от MT5: {}", message);
        } catch (Exception e) {
            log.error("Ошибка обработки тика: {}", message, e);
        }
    }
}