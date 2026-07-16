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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 전자결재 내문서함(기안)에서 올해 휴가신청서 문서를 열어
 * 각 줄의 "종일" 항목과 "4시간"(반차) 항목을 분리해 반환한다.
 */
public class VacationChecker {

    private static final Logger log = LoggerFactory.getLogger(VacationChecker.class);

    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");

    public static class Result {
        public final Set<LocalDate> fullDays;  // 종일 연차
        public final Set<LocalDate> halfDays;  // 오후반차 (4시간)

        public Result(Set<LocalDate> fullDays, Set<LocalDate> halfDays) {
            this.fullDays = fullDays;
            this.halfDays = halfDays;
        }
    }

    public static Result parse(WebDriver driver, String approvalUrl) {
        Set<LocalDate> fullDays = new HashSet<>();
        Set<LocalDate> halfDays = new HashSet<>();
        try {
            String company = extractCompany(approvalUrl);
            List<String> docUrls = collectDocumentUrls(driver, approvalUrl, company);
            log.info("올해 휴가신청서 {}개 상세 분석", docUrls.size());
            for (String url : docUrls) {
                parseDocumentDetail(driver, url, fullDays, halfDays);
            }
            log.info("연차(종일) {}개: {}", fullDays.size(), fullDays);
            log.info("반차(4시간) {}개: {}", halfDays.size(), halfDays);
        } catch (Exception e) {
            log.error("전자결재 연차 파싱 실패: {}", e.getMessage());
        }
        return new Result(fullDays, halfDays);
    }

    private static String extractCompany(String approvalUrl) {
        String[] parts = approvalUrl.split("/");
        return parts.length > 3 ? parts[3] : "";
    }

    private static List<String> collectDocumentUrls(WebDriver driver, String approvalUrl, String company)
            throws InterruptedException {
        List<String> urls = new ArrayList<>();
        int thisYear = LocalDate.now().getYear();

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

            List<WebElement> rows = driver.findElements(
                    By.cssSelector("#tableApprovalDocumentBox thead tr")
            );
            for (int i = 1; i < rows.size(); i++) {
                try {
                    WebElement row = rows.get(i);
                    String formType = row.findElement(By.cssSelector("td.docu-form div")).getText().trim();
                    if (!formType.equals("휴가신청서")) continue;

                    String title = row.findElement(By.cssSelector("td.title")).getText().trim();
                    if (!title.contains(thisYear + "년")) continue;

                    String docUrl = resolveDocUrl(row, company);
                    if (docUrl != null) {
                        urls.add(docUrl);
                        log.debug("문서 URL 수집: {} | {}", title, docUrl);
                    }
                } catch (Exception ignored) { }
            }
        }
        return urls;
    }

    private static String resolveDocUrl(WebElement row, String company) {
        try {
            String href = row.findElement(By.cssSelector("td.title a")).getAttribute("href");
            if (href != null && !href.isEmpty()) return href;
        } catch (Exception ignored) { }

        try {
            String docId = row.findElement(By.cssSelector("input[type='checkbox']")).getAttribute("value");
            if (docId != null && !docId.isEmpty()) {
                return String.format(
                        "https://approval.office.hiworks.com/%s/approval/document/view/%s",
                        company, docId
                );
            }
        } catch (Exception ignored) { }

        return null;
    }

    private static void parseDocumentDetail(WebDriver driver, String url,
                                            Set<LocalDate> fullDays, Set<LocalDate> halfDays) {
        try {
            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//th[contains(text(),'휴가 신청')]")
                    )
            );
            Thread.sleep(800);

            int thisYear = LocalDate.now().getYear();
            List<WebElement> paragraphs = driver.findElements(
                    By.xpath("//tr[th[contains(text(),'휴가 신청')]]/td//p")
            );

            for (WebElement p : paragraphs) {
                String text = p.getText().trim();
                if (text.isEmpty()) continue;

                Matcher m = MONTH_DAY.matcher(text);
                if (!m.find()) continue;

                LocalDate date;
                try {
                    date = LocalDate.of(thisYear, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                } catch (Exception ignored) { continue; }

                if (text.contains("종일")) {
                    fullDays.add(date);
                    log.info("연차(종일) 등록: {}", date);
                } else if (text.contains("4시간") || text.matches(".*\\d{1,2}:\\d{2}~\\d{1,2}:\\d{2}.*")) {
                    halfDays.add(date);
                    log.info("반차(4시간) 등록: {}", date);
                }
            }
        } catch (Exception e) {
            log.warn("문서 상세 파싱 실패 ({}): {}", url, e.getMessage());
        }
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
}
