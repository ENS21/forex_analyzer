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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.time.ZoneOffset;

@Component
public class MT5CandleSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MT5CandleSubscriber.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final CandleRepository candleRepository;

    public MT5CandleSubscriber(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    @PostConstruct
    public void start() {
        Thread thread = new Thread(this::listen, "mt5-candle-subscriber");
        thread.setDaemon(true);
        thread.start();
    }

    private void listen() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://localhost:5555");
            subscriber.subscribe("candle.EURUSD".getBytes());
            log.info("Подписался на свечи MT5 (candle.EURUSD)");

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