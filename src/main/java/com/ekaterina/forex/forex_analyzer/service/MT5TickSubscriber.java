package com.ekaterina.forex.forex_analyzer.service;

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
            subscriber.subscribe("tick.EURUSD".getBytes());
            log.info("Подключился к MT5 ZeroMQ на порту 5555");

            while (!Thread.currentThread().isInterrupted()) {
                String message = subscriber.recvStr();
                if (message != null) {
                    log.info("Тик от MT5: {}", message);
                }
            }
        }
    }
}