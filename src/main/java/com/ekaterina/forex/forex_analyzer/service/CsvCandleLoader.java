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

/**
 * Сервис для первичной инициализации базы данных историческими данными.
 * <p>
 * Реализует интерфейс {@link CommandLineRunner}, благодаря чему метод {@code run()}
 * автоматически выполняется один раз при запуске Spring Boot приложения.
 * Читает CSV-файл, парсит его и сохраняет свечи в БД (если база пуста).
 * </p>
 */
@Service
public class CsvCandleLoader implements CommandLineRunner {

    private final CandleRepository candleRepository;

    /**
     * Конструктор для внедрения зависимости репозитория.
     *
     * @param candleRepository Репозиторий для сохранения свечей.
     */
    public CsvCandleLoader(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /**
     * Основной метод, выполняющийся при старте приложения.
     * <p>
     * Алгоритм работы:
     * <ol>
     *     <li>Проверяет наличие CSV-файла по указанному пути.</li>
     *     <li>Проверяет, есть ли уже данные в базе (если есть — загрузка пропускается).</li>
     *     <li>Читает файл, парсит строки и сохраняет новые свечи в базу данных.</li>
     * </ol>
     * </p>
     *
     * @param args Аргументы командной строки (не используются в данной реализации).
     * @throws Exception Если возникают ошибки при чтении файла или записи в БД.
     */

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