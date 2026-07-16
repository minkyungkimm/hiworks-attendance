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

public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private static volatile TelegramBotService instance;

    private final String apiBase;
    private final String chatId;
    private final HttpClient httpClient;
    private volatile boolean running = false;
    private volatile boolean morningHalfdayAsked = false;
    private volatile boolean halfdayAsked = false;
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
            morningHalfdayAsked = false;
            halfdayAsked = false;

            JSONArray row1 = new JSONArray();
            row1.put(new JSONObject().put("text", "🕗 8시 출근").put("callback_data", "CHECKIN_8"));
            row1.put(new JSONObject().put("text", "🕘 9시 출근").put("callback_data", "CHECKIN_9"));

            JSONArray row2 = new JSONArray();
            row2.put(new JSONObject().put("text", "🌄 오전 반차").put("callback_data", "MORNING_HALFDAY"));
            row2.put(new JSONObject().put("text", "🌅 오후 반차").put("callback_data", "HALFDAY"));

            JSONArray row3 = new JSONArray();
            row3.put(new JSONObject().put("text", "🏖️ 오늘 연차").put("callback_data", "VACATION"));

            JSONObject replyMarkup = new JSONObject();
            replyMarkup.put("inline_keyboard", new JSONArray().put(row1).put(row2).put(row3));

            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", "🕐 오늘 몇시 출근하시나요?\n\n버튼을 누르지 않으면 8시 자동 출근 처리됩니다.");
            body.put("reply_markup", replyMarkup);

            HttpResponse<String> response = post("/sendMessage", body.toString());
            JSONObject result = new JSONObject(response.body());
            if (result.getBoolean("ok")) {
                log.info("텔레그램 출근 알림 발송 완료 (message_id={})",
                        result.getJSONObject("result").getLong("message_id"));
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

        AttendanceState.Decision current = AttendanceState.get();

        // 이미 설정된 상태면 어떤 버튼을 눌러도 토스트 알림만 표시
        if (current != AttendanceState.Decision.PENDING) {
            answerCallbackQuery(queryId, alreadySetMessage(current));
            return;
        }

        switch (data) {
            case "CHECKIN_8": {
                AttendanceState.set(AttendanceState.Decision.CHECKIN_8);
                log.info("텔레그램 버튼 클릭: CHECKIN_8");
                answerCallbackQuery(queryId);
                sendMessage("✅ 8시 출근으로 설정되었습니다. 8시에 자동 출근 처리됩니다.");
                break;
            }
            case "CHECKIN_9": {
                AttendanceState.set(AttendanceState.Decision.CHECKIN_9);
                log.info("텔레그램 버튼 클릭: CHECKIN_9");
                answerCallbackQuery(queryId);
                sendMessage("✅ 9시 출근으로 설정되었습니다. 9시에 자동 출근 처리됩니다.");
                break;
            }
            case "MORNING_HALFDAY": {
                if (morningHalfdayAsked) {
                    answerCallbackQuery(queryId, "이미 출근 시간 선택 중입니다. 아래 버튼을 눌러주세요.");
                    return;
                }
                morningHalfdayAsked = true;
                log.info("텔레그램 버튼 클릭: MORNING_HALFDAY → 기준 선택 메시지 전송");
                answerCallbackQuery(queryId);
                sendMorningHalfdayTimeQuestion();
                break;
            }
            case "MORNING_HALFDAY_8": {
                AttendanceState.set(AttendanceState.Decision.MORNING_HALFDAY_8);
                log.info("텔레그램 버튼 클릭: MORNING_HALFDAY_8 → 오전반차 8시 기준");
                answerCallbackQuery(queryId);
                sendMessage("🌄 1시 출근 + 오전 반차로 설정되었습니다.\n1시 출근 → 17시 자동 퇴근 처리됩니다.");
                break;
            }
            case "MORNING_HALFDAY_9": {
                AttendanceState.set(AttendanceState.Decision.MORNING_HALFDAY_9);
                log.info("텔레그램 버튼 클릭: MORNING_HALFDAY_9 → 오전반차 9시 기준");
                answerCallbackQuery(queryId);
                sendMessage("🌄 2시 출근 + 오전 반차로 설정되었습니다.\n2시 출근 → 18시 자동 퇴근 처리됩니다.");
                break;
            }
            case "HALFDAY": {
                if (halfdayAsked) {
                    answerCallbackQuery(queryId, "이미 출근 시간 선택 중입니다. 아래 버튼을 눌러주세요.");
                    return;
                }
                halfdayAsked = true;
                log.info("텔레그램 버튼 클릭: HALFDAY → 출근시간 선택 메시지 전송");
                answerCallbackQuery(queryId);
                sendHalfdayTimeQuestion();
                break;
            }
            case "HALFDAY_8": {
                AttendanceState.set(AttendanceState.Decision.HALFDAY_8);
                log.info("텔레그램 버튼 클릭: HALFDAY_8 → 8시 출근 + 오후 반차");
                answerCallbackQuery(queryId);
                sendMessage("🌅 8시 출근 + 오후 반차로 설정되었습니다.\n8시 출근 → 12시 자동 퇴근 처리됩니다.");
                break;
            }
            case "HALFDAY_9": {
                AttendanceState.set(AttendanceState.Decision.HALFDAY_9);
                log.info("텔레그램 버튼 클릭: HALFDAY_9 → 9시 출근 + 오후 반차");
                answerCallbackQuery(queryId);
                sendMessage("🌅 9시 출근 + 오후 반차로 설정되었습니다.\n9시 출근 → 14시 자동 퇴근 처리됩니다.");
                break;
            }
            case "VACATION": {
                AttendanceState.set(AttendanceState.Decision.VACATION);
                log.info("텔레그램 버튼 클릭: VACATION");
                answerCallbackQuery(queryId);
                sendMessage("🏖️ 연차로 설정되었습니다. 오늘 출퇴근 체크를 건너뜁니다.");
                break;
            }
            default:
                break;
        }
    }

    private static String alreadySetMessage(AttendanceState.Decision state) {
        switch (state) {
            case CHECKIN_8:         return "이미 8시 출근으로 설정되어 있습니다";
            case CHECKIN_9:         return "이미 9시 출근으로 설정되어 있습니다";
            case MORNING_HALFDAY_8: return "이미 오전반차(1시 출근)로 설정되어 있습니다";
            case MORNING_HALFDAY_9: return "이미 오전반차(2시 출근)로 설정되어 있습니다";
            case HALFDAY_8:         return "이미 오후반차(8시 출근)로 설정되어 있습니다";
            case HALFDAY_9:         return "이미 오후반차(9시 출근)로 설정되어 있습니다";
            case VACATION:          return "이미 연차로 설정되어 있습니다";
            default:                return "이미 설정되어 있습니다";
        }
    }

    private void sendMorningHalfdayTimeQuestion() {
        try {
            JSONArray row = new JSONArray();
            row.put(new JSONObject().put("text", "🕐 1시 출근").put("callback_data", "MORNING_HALFDAY_8"));
            row.put(new JSONObject().put("text", "🕑 2시 출근").put("callback_data", "MORNING_HALFDAY_9"));

            JSONObject replyMarkup = new JSONObject();
            replyMarkup.put("inline_keyboard", new JSONArray().put(row));

            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", "🌄 오전 반차 - 몇 시 출근하시나요?");
            body.put("reply_markup", replyMarkup);

            HttpResponse<String> response = post("/sendMessage", body.toString());
            JSONObject result = new JSONObject(response.body());
            if (result.getBoolean("ok")) {
                log.info("오전반차 기준 선택 메시지 전송 완료 (message_id={})",
                        result.getJSONObject("result").getLong("message_id"));
            } else {
                log.warn("오전반차 기준 선택 메시지 전송 실패: {}", response.body());
            }
        } catch (Exception e) {
            log.error("오전반차 기준 선택 메시지 전송 중 오류: {}", e.getMessage());
        }
    }

    private void sendHalfdayTimeQuestion() {
        try {
            JSONArray row = new JSONArray();
            row.put(new JSONObject().put("text", "🕗 8시 출근").put("callback_data", "HALFDAY_8"));
            row.put(new JSONObject().put("text", "🕘 9시 출근").put("callback_data", "HALFDAY_9"));

            JSONObject replyMarkup = new JSONObject();
            replyMarkup.put("inline_keyboard", new JSONArray().put(row));

            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", "🌅 오후 반차 - 몇 시에 출근하시나요?");
            body.put("reply_markup", replyMarkup);

            HttpResponse<String> response = post("/sendMessage", body.toString());
            JSONObject result = new JSONObject(response.body());
            if (result.getBoolean("ok")) {
                log.info("반차 출근시간 선택 메시지 전송 완료 (message_id={})",
                        result.getJSONObject("result").getLong("message_id"));
            } else {
                log.warn("반차 출근시간 선택 메시지 전송 실패: {}", response.body());
            }
        } catch (Exception e) {
            log.error("반차 출근시간 선택 메시지 전송 중 오류: {}", e.getMessage());
        }
    }

    private void answerCallbackQuery(String queryId) {
        answerCallbackQuery(queryId, null);
    }

    private void answerCallbackQuery(String queryId, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("callback_query_id", queryId);
            if (text != null) body.put("text", text);
            post("/answerCallbackQuery", body.toString());
        } catch (Exception e) {
            log.error("answerCallbackQuery 실패: {}", e.getMessage());
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
