package com.attendance;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class HolidayChecker {

    private static final Logger log = LoggerFactory.getLogger(HolidayChecker.class);
    private static final String API_URL = "https://date.nager.at/api/v3/PublicHolidays/%d/KR";

    private static int cachedYear = -1;
    private static Set<LocalDate> cachedHolidays = Collections.emptySet();

    public static boolean isHoliday(LocalDate date) {
        ensureCache(date.getYear());
        boolean holiday = cachedHolidays.contains(date);
        if (holiday) {
            log.info("오늘({})은 공휴일입니다.", date);
        }
        return holiday;
    }

    private static synchronized void ensureCache(int year) {
        if (cachedYear == year) return;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String url = String.format(API_URL, year);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONArray arr = new JSONArray(response.body());

                Set<LocalDate> holidays = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    holidays.add(LocalDate.parse(obj.getString("date")));
                }

                cachedYear = year;
                cachedHolidays = holidays;
                log.info("{}년 한국 공휴일 {}개 로드 완료", year, holidays.size());
                return;

            } catch (Exception e) {
                log.warn("공휴일 API 호출 실패 (시도 {}/3): {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        // 3회 모두 실패 - cachedYear 미설정으로 다음 잡 실행 시 재시도
        log.warn("공휴일 API 3회 모두 실패 - 오늘은 공휴일 체크 없이 진행, 다음 실행 시 재시도합니다.");
    }
}
