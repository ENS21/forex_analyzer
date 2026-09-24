# Forex Analyzer

Spring Boot приложение для анализа валютных пар.
Показывает RSI (индекс относительной силы) и сигналы
перекупленности/перепроданности для EURUSD.

## Стек технологий
- Java 21
- Spring Boot 4.0.8
- ta4j 0.15 — библиотека технического анализа
- Maven

## Эндпоинты

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/analytics/EURUSD` | RSI + сигнал по EURUSD |

## Пример ответа
```json
{
  "pair": "EURUSD",
  "currentPrice": 1.107,
  "rsi": 82.92,
  "signal": "ПЕРЕКУПЛЕНА"
}
```

## Как запустить
1. Клонировать репозиторий: `git clone https://github.com/ENS21/forex_analyzer.git`
2. Открыть в IntelliJ IDEA
3. Запустить `ForexAnalyzerApplication`
4. Открыть в браузере: `http://localhost:8080/api/analytics/EURUSD`

## Планы развития
- [ ] Подключение к MetaTrader 5 через WebRequest/ZeroMQ
- [ ] Волатильность по дням недели
- [ ] Определение краткосрочного/среднесрочного/долгосрочного тренда
- [ ] Telegram-бот для уведомлений
- [ ] Мобильное приложение (Android)