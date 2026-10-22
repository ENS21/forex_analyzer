package com.ekaterina.forex.forex_analyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Сервис для отправки уведомлений в MAX (с обходом проверки SSL-сертификата).
 */
@Service
public class MaxNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MaxNotificationService.class);
    private final HttpClient httpClient;
    private final String token;
    private final Long chatId;

    public MaxNotificationService(
            @Value("${max.bot.token}") String token,
            @Value("${max.bot.chat-id}") Long chatId) throws Exception {
        this.token = token;
        this.chatId = chatId;

        // Настраиваем HttpClient, который не проверяет SSL-сертификаты (только для разработки!)
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        // Отключаем проверку имени хоста (нужно для Java 11+)
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

        this.httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
    }

    /**
     * Отправляет текстовое сообщение в канал мессенджера MAX.
     * <p>
     * Метод формирует HTTP POST-запрос к API MAX, передавая {@code chat_id} в URL,
     * а текст сообщения — в теле запроса в формате JSON. Токен авторизации
     * передается в заголовке.
     * </p>
     * <p>
     * <b>Важно:</b> Метод перехватывает и логирует все исключения (например, сетевые ошибки
     * или ошибки SSL-сертификатов), чтобы сбой мессенджера не прерывал основной
     * бизнес-процесс приложения (например, расчет RSI).
     * </p>
     *
     * @param text Текст сообщения, который нужно отправить в канал.
     */

    public void sendSignal(String text) {
        try {
            String safeText = text.replace("\"", "\\\"").replace("\n", "\\n");
            // Передаем chat_id канала в URL
            String url = String.format("https://platform-api2.max.ru/messages?chat_id=%d", chatId);
            String jsonBody = String.format("{\"text\":\"%s\"}", safeText);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", token) // Токен в заголовке
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Ответ от MAX ({}): {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Не удалось отправить сообщение в MAX. Текст: {}", text, e);
        }
    }
}