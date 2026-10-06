package com.ekaterina.forex.forex_analyzer.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Service
public class TelegramService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.chat.id}")
    private Long chatId;

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Бот пока не обрабатывает команды от пользователей
        // Здесь позже можно добавить обработку /start, /help и т.д.
    }

    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(this);
            log.info("Telegram-бот зарегистрирован: @{}", botUsername);
        } catch (TelegramApiException e) {
            log.error("Ошибка регистрации Telegram-бота", e);
        }
    }

    /**
     * Отправляет сообщение в указанный чат.
     */
    public void sendMessage(String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
            log.info("Сообщение отправлено в Telegram: {}", text);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения в Telegram", e);
        }
    }
}