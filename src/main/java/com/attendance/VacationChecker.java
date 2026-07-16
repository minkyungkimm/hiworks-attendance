package com.attendance;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 전자결재 내문서함 > 기안 > 휴가신청서에서 연차 사용 날짜를 파싱합니다.
 * - 문서종류가 "휴가신청서"인 항목만 처리
 * - 날짜는 제목(title)에서 추출 (기안일/완료일 무시)
 * - (4시간) 반차는 제외 (전일 연차만 자동 처리)
 * - 날짜 범위(YYYY년 M월 D일~YYYY년 M월 D일)는 전체 구간 추가
 */
public class VacationChecker {

    private static final Logger log = LoggerFactory.getLogger(VacationChecker.class);

    private static final Pattern RANGE_DATE = Pattern.compile(
            "(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일~(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일"
    );
    private static final Pattern SINGLE_DATE = Pattern.compile(
            "(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일"
    );

    public static Set<LocalDate> parse(WebDriver driver, String approvalUrl) {
        Set<LocalDate> vacationDates = new HashSet<>();
        try {
            log.info("전자결재 내문서함(기안) 이동: {}", approvalUrl);
            driver.get(approvalUrl);

            new WebDriverWait(driver, Duration.ofSeconds(20)).until(
                    ExpectedConditions.presenceOfElementLocated(By.id("tableApprovalDocumentBox"))
            );
            Thread.sleep(2000);

            for (int page = 1; page <= 3; page++) {
                if (page > 1) {
                    String firstId = getFirstRowId(driver);
                    ((JavascriptExecutor) driver).executeScript(
                            "ApprovalDocument.getDocumentBoxListByPage('" + page + "')"
                    );
                    // 페이지 전환 완료 대기 (첫 행 ID 변경 확인)
                    String newFirstId = firstId;
                    for (int retry = 0; retry < 20; retry++) {
                        Thread.sleep(300);
                        newFirstId = getFirstRowId(driver);
                        if (newFirstId != null && !newFirstId.equals(firstId)) break;
                    }
                    if (newFirstId == null || newFirstId.equals(firstId)) {
                        log.info("페이지 {} 전환 실패 또는 마지막 페이지 — 중단", page);
                        break;
                    }
                }
                parsePage(driver, vacationDates);
            }

            log.info("연차 날짜 {}개 확인: {}", vacationDates.size(), vacationDates);
        } catch (Exception e) {
            log.error("전자결재 연차 파싱 실패: {}", e.getMessage());
        }
        return vacationDates;
    }

    private static String getFirstRowId(WebDriver driver) {
        try {
            List<WebElement> rows = driver.findElements(
                    By.cssSelector("#tableApprovalDocumentBox thead tr")
            );
            if (rows.size() > 1) {
                return rows.get(1)
                        .findElement(By.cssSelector("input[type='checkbox']"))
                        .getAttribute("value");
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static void parsePage(WebDriver driver, Set<LocalDate> dates) {
        try {
            List<WebElement> rows = driver.findElements(
                    By.cssSelector("#tableApprovalDocumentBox thead tr")
            );
            for (int i = 1; i < rows.size(); i++) {
                WebElement row = rows.get(i);
                try {
                    String formType = row.findElement(By.cssSelector("td.docu-form div")).getText().trim();
                    if (!formType.equals("휴가신청서")) continue;

                    String title = row.findElement(By.cssSelector("td.title")).getText().trim();
                    log.debug("휴가신청서 제목 파싱: {}", title);
                    extractDates(title, dates);
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            log.warn("페이지 파싱 오류: {}", e.getMessage());
        }
    }

    private static void extractDates(String title, Set<LocalDate> dates) {
        int thisYear = LocalDate.now().getYear();

        // 날짜 범위 우선 처리: YYYY년 M월 D일~YYYY년 M월 D일
        Matcher rangeMatcher = RANGE_DATE.matcher(title);
        if (rangeMatcher.find()) {
            LocalDate start = toDate(rangeMatcher.group(1), rangeMatcher.group(2), rangeMatcher.group(3));
            LocalDate end   = toDate(rangeMatcher.group(4), rangeMatcher.group(5), rangeMatcher.group(6));
            if (start != null && end != null && !end.isBefore(start)) {
                start.datesUntil(end.plusDays(1))
                        .filter(d -> d.getYear() == thisYear)
                        .forEach(dates::add);
                log.info("범위 연차 등록: {} ~ {}", start, end);
            }
            return;
        }

        // 반차(4시간)는 자동 처리 제외 — 텔레그램 버튼으로 수동 선택
        if (title.contains("(4시간)")) {
            log.debug("반차(4시간) — 자동 처리 제외: {}", title);
            return;
        }

        // 단일 날짜
        Matcher singleMatcher = SINGLE_DATE.matcher(title);
        if (singleMatcher.find()) {
            LocalDate date = toDate(singleMatcher.group(1), singleMatcher.group(2), singleMatcher.group(3));
            if (date != null && date.getYear() == thisYear) {
                dates.add(date);
                log.info("연차 날짜 등록: {}", date);
            }
        }
    }

    private static LocalDate toDate(String year, String month, String day) {
        try {
            return LocalDate.of(
                    Integer.parseInt(year),
                    Integer.parseInt(month),
                    Integer.parseInt(day)
            );
        } catch (Exception e) {
            return null;
        }
    }
}
