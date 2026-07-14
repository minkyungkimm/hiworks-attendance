package com.attendance;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private static volatile TelegramBotService instance;

    private final String apiBase;
    private final String chatId;
    private final HttpClient httpClient;
    private final AtomicLong lastReminderMessageId = new AtomicLong(-1);
    private volatile boolean running = false;
    private Thread pollingThread;

    private TelegramBotService(String botToken, String chatId) {
        this.apiBase = "https://api.telegram.org/bot" + botToken;
        this.chatId = chatId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static TelegramBotService create(String botToken, String chatId) {
        instance = new TelegramBotService(botToken, chatId);
        return instance;
    }

    public static TelegramBotService getInstance() {
        return instance;
    }

    // 단순 텍스트 메시지 전송
    public void sendMessage(String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", text);
            post("/sendMessage", body.toString());
            log.info("텔레그램 메시지 전송: {}", text);
        } catch (Exception e) {
            log.error("텔레그램 메시지 전송 실패: {}", e.getMessage());
        }
    }

    // 7:30 알림 메시지 전송 + 상태 초기화
    public void sendReminderMessage() {
        try {
            AttendanceState.reset();

            JSONArray row = new JSONArray();
            row.put(new JSONObject().put("text", "✅ 네, 8시 출근").put("callback_data", "CHECKIN_8"));
            row.put(new JSONObject().put("text", "❌ 아니요, 9시").put("callback_data", "CHECKIN_9"));

            JSONObject replyMarkup = new JSONObject();
            replyMarkup.put("inline_keyboard", new JSONArray().put(row));

            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", "🕗 오늘 8시에 출근하시나요?\n\n버튼을 누르지 않으면 8시 자동 출근 처리됩니다.");
            body.put("reply_markup", replyMarkup);

            HttpResponse<String> response = post("/sendMessage", body.toString());
            JSONObject result = new JSONObject(response.body());
            if (result.getBoolean("ok")) {
                long msgId = result.getJSONObject("result").getLong("message_id");
                lastReminderMessageId.set(msgId);
                log.info("텔레그램 출근 알림 발송 완료 (message_id={})", msgId);
            } else {
                log.warn("텔레그램 메시지 전송 실패: {}", response.body());
            }
        } catch (Exception e) {
            log.error("텔레그램 메시지 전송 중 오류: {}", e.getMessage(), e);
        }
    }

    // 백그라운드 폴링 스레드 시작
    public void startPolling() {
        running = true;
        pollingThread = new Thread(this::pollingLoop, "telegram-polling");
        pollingThread.setDaemon(true);
        pollingThread.start();
        log.info("텔레그램 봇 폴링 시작 (버튼 응답 대기 중)");
    }

    public void stopPolling() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        log.info("텔레그램 봇 폴링 종료");
    }

    private void pollingLoop() {
        long offset = 0;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                String url = apiBase + "/getUpdates?offset=" + offset + "&timeout=25";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(35))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject json = new JSONObject(response.body());

                if (json.getBoolean("ok")) {
                    JSONArray results = json.getJSONArray("result");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject update = results.getJSONObject(i);
                        offset = update.getLong("update_id") + 1;
                        processUpdate(update);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    log.error("텔레그램 폴링 오류 (5초 후 재시도): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void processUpdate(JSONObject update) {
        if (!update.has("callback_query")) return;

        JSONObject callbackQuery = update.getJSONObject("callback_query");
        String queryId = callbackQuery.getString("id");
        String data = callbackQuery.getString("data");

        AttendanceState.Decision decision;
        String confirmText;

        switch (data) {
            case "CHECKIN_8":
                decision = AttendanceState.Decision.CHECKIN_8;
                confirmText = "✅ 8시 출근으로 설정되었습니다. 8:00에 자동 출근 처리됩니다.";
                break;
            case "CHECKIN_9":
                decision = AttendanceState.Decision.CHECKIN_9;
                confirmText = "✅ 9시 출근으로 설정되었습니다. 9:00에 자동 출근 처리됩니다.";
                break;
            default:
                return;
        }

        AttendanceState.set(decision);
        log.info("텔레그램 버튼 클릭: {} → {}", data, decision);

        answerCallbackQuery(queryId, confirmText);

        long msgId = lastReminderMessageId.get();
        if (msgId > 0) {
            editMessage(msgId, confirmText);
        }
    }

    private void answerCallbackQuery(String queryId, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("callback_query_id", queryId);
            body.put("text", text);
            post("/answerCallbackQuery", body.toString());
        } catch (Exception e) {
            log.error("answerCallbackQuery 실패: {}", e.getMessage());
        }
    }

    private void editMessage(long messageId, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("text", text);
            post("/editMessageText", body.toString());
        } catch (Exception e) {
            log.error("editMessageText 실패: {}", e.getMessage());
        }
    }

    private HttpResponse<String> post(String endpoint, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + endpoint))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
