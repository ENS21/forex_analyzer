package com.ekaterina.forex.forex_analyzer.service;

import com.ekaterina.forex.forex_analyzer.entity.Candle;
import com.ekaterina.forex.forex_analyzer.repository.CandleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvCandleLoader implements CommandLineRunner {

    private final CandleRepository candleRepository;

    public CsvCandleLoader(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        String csvPath = "C:/Users/Admin/AppData/Roaming/MetaQuotes/Terminal/FA97EA291D4188820508F9D2B5AAD50F/MQL5/Files/EURUSD_D1.csv";

        Path path = Paths.get(csvPath);
        if (!Files.exists(path)) {
            System.out.println("Файл не найден: " + csvPath);
            return;
        }

        System.out.println("Начинаю загрузку свечей из " + csvPath);


        long existing = candleRepository.count();
        if (existing > 0) {
            System.out.println("В БД уже " + existing + " свечей. Пропускаю загрузку.");
            return;
        }

        List<Candle> candles = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // пропускаем заголовок
                }

                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                Candle candle = new Candle();
                candle.setSymbol("EURUSD");
                candle.setTimeframe("D1");
                candle.setOpenTime(LocalDateTime.parse(parts[0].trim(), formatter));
                candle.setOpen(new BigDecimal(parts[1].trim()));
                candle.setHigh(new BigDecimal(parts[2].trim()));
                candle.setLow(new BigDecimal(parts[3].trim()));
                candle.setClose(new BigDecimal(parts[4].trim()));
                candle.setVolume(new BigDecimal(parts[5].trim()));

                candles.add(candle);
            }
        }

        candleRepository.saveAll(candles);
        System.out.println("Загружено свечей: " + candles.size());
        System.out.println("Всего свечей в БД: " + candleRepository.count());
    }
}