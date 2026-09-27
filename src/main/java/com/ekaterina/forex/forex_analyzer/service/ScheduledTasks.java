package com.ekaterina.forex.forex_analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    /**
     * Задача обновления данных с MT5.
     * Пока в виде заглушки — пишет в лог, что наступило время обновления.
     * Позже сюда добавим автоматическую загрузку свечей из MT5.
     *
     * cron = "0 0 1 * * *" — каждый день в 01:00 ночи.
     * для проверки fixedRate = 30000
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void refreshCandleData() {
        log.info("=== [SCHEDULED] Наступило время обновления свечей из MT5 ===");
        log.info("=== [SCHEDULED] Пока это заглушка — данные обновляются вручную через CSV ===");
        log.info("=== [SCHEDULED] В будущем здесь будет автоматическая загрузка через ZeroMQ/WebRequest ===");
    }
}