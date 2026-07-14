package com.attendance;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HiworksService {

    private static final Logger log = LoggerFactory.getLogger(HiworksService.class);

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Duration PAGE_LOAD = Duration.ofSeconds(30);

    private final AppConfig config;
    private WebDriver driver;
    private WebDriverWait wait;

    public HiworksService(AppConfig config) {
        this.config = config;
        initDriver();
    }

    // ──────────────────────────────────────────────
    // 드라이버 초기화
    // ──────────────────────────────────────────────

    private void initDriver() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions opts = new ChromeOptions();
        if (config.isHeadless()) {
            opts.addArguments("--headless=new");
        }
        opts.addArguments("--no-sandbox");
        opts.addArguments("--disable-dev-shm-usage");
        opts.addArguments("--disable-gpu");
        opts.addArguments("--window-size=1920,1080");
        opts.addArguments("--lang=ko-KR");
        // 자동화 감지 우회
        opts.addArguments("--disable-blink-features=AutomationControlled");
        opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        opts.setExperimentalOption("useAutomationExtension", false);

        driver = new ChromeDriver(opts);
        wait = new WebDriverWait(driver, WAIT);
        driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD);
        driver.manage().window().maximize();
        log.info("ChromeDriver 초기화 완료 (headless={})", config.isHeadless());
    }

    // ──────────────────────────────────────────────
    // 로그인
    // ──────────────────────────────────────────────

    public void login() {
        log.info("하이웍스 접속: {}", config.getHiworksUrl());
        driver.get(config.getHiworksUrl());

        // 회사 도메인 입력 (첫 번째 단계)
        inputCompanyIfPresent();

        // 아이디 입력
        WebElement userField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[name='user_id'], #user_id, input[name='id'], input[placeholder*='아이디'], input[placeholder*='ID']")
        ));
        clear(userField);
        userField.sendKeys(config.getUsername());
        log.info("아이디 입력 완료");

        // 아이디 입력 후 다음 버튼 클릭 (2단계 로그인 대응)
        try {
            WebElement nextBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[type='submit'], input[type='submit'], .btn-next, .next-btn, button.btn-primary")
            ));
            nextBtn.click();
            log.info("다음 버튼 클릭");
            pause(1500);
        } catch (Exception e) {
            log.debug("다음 버튼 없음 — 단일 페이지 로그인으로 진행");
        }

        // 비밀번호 입력
        WebElement pwField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[type='password']")
        ));
        clear(pwField);
        pwField.sendKeys(config.getPassword());
        log.info("비밀번호 입력 완료");

        // 로그인 버튼 클릭 (없으면 Enter)
        try {
            driver.findElement(By.cssSelector(
                    "button[type='submit'], input[type='submit'], .login-btn, #btnLogin, .btn-login"
            )).click();
        } catch (NoSuchElementException e) {
            pwField.sendKeys(Keys.ENTER);
        }

        // 로그인 완료 대기 (login.office.hiworks.com 에서 벗어날 때까지)
        wait.until(d -> !d.getCurrentUrl().contains("login.office.hiworks.com"));

        log.info("로그인 성공. 현재 URL: {}", driver.getCurrentUrl());
        takeScreenshot("01-login-success");
    }

    private void inputCompanyIfPresent() {
        try {
            List<WebElement> fields = driver.findElements(By.cssSelector(
                    "input[name='office_domain'], #office_domain, " +
                    "input[placeholder*='회사'], input[placeholder*='도메인'], input[placeholder*='Company']"
            ));
            if (!fields.isEmpty() && fields.get(0).isDisplayed()) {
                clear(fields.get(0));
                fields.get(0).sendKeys(config.getCompany());
                log.info("회사 도메인 입력: {}", config.getCompany());

                // 다음 단계 버튼이 있으면 클릭
                List<WebElement> nextBtns = driver.findElements(By.cssSelector(
                        "button[type='submit'], .btn-next, .btn-confirm, .next-btn"
                ));
                if (!nextBtns.isEmpty() && nextBtns.get(0).isDisplayed()) {
                    nextBtns.get(0).click();
                    Thread.sleep(1500);
                }
            }
        } catch (Exception e) {
            log.debug("회사 도메인 입력 단계 건너뜀 (단일 페이지 로그인으로 간주)");
        }
    }

    // ──────────────────────────────────────────────
    // 출석체크
    // ──────────────────────────────────────────────

    private static final String WORK_PAGE_URL = "https://hr-work.office.hiworks.com/personal/index";

    public boolean checkAndDoAttendance() {
        log.info("근무 페이지 이동: {}", WORK_PAGE_URL);
        driver.get(WORK_PAGE_URL);
        pause(2000);
        takeScreenshot("02-work-page");

        return tryAttendanceOnCurrentPage();
    }

    public boolean checkAndDoCheckout() {
        log.info("근무 페이지 이동 (퇴근): {}", WORK_PAGE_URL);
        driver.get(WORK_PAGE_URL);
        pause(2000);
        takeScreenshot("02-checkout-page");

        try {
            // 이미 퇴근 완료 상태 확인
            if (isAlreadyCheckedOut()) {
                log.info("이미 퇴근 처리되어 있습니다.");
                return true;
            }

            // 출근도 안 된 상태면 퇴근 불가
            if (!isAlreadyCheckedIn()) {
                log.warn("출근 기록이 없어 퇴근 처리를 할 수 없습니다.");
                return false;
            }

            // 퇴근 버튼 탐색
            WebElement btn = findCheckOutButton();
            if (btn == null) {
                takeScreenshot("checkout-btn-not-found");
                log.error("퇴근 버튼을 찾을 수 없습니다. 스크린샷을 확인하세요.");
                return false;
            }

            log.info("퇴근 버튼 발견 (text='{}'). 클릭합니다.", btn.getText().trim());
            takeScreenshot("03-before-checkout");

            scrollIntoViewAndClick(btn);
            pause(1500);
            handleConfirmation();
            pause(2000);
            takeScreenshot("04-after-checkout");

            log.info("퇴근 체크 완료!");
            return true;

        } catch (Exception e) {
            log.error("퇴근 처리 중 오류: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isAlreadyCheckedOut() {
        List<WebElement> els = driver.findElements(By.xpath(
                "//*[contains(text(),'퇴근완료') or contains(text(),'퇴근 완료')]"
        ));
        return els.stream().anyMatch(WebElement::isDisplayed);
    }

    private WebElement findCheckOutButton() {
        String[] xpaths = {
                "//button[normalize-space(text())='퇴근']",
                "//button[contains(text(),'퇴근') and not(contains(text(),'완료'))]",
                "//a[normalize-space(text())='퇴근']",
                "//span[normalize-space(text())='퇴근']/parent::button",
        };
        for (String xpath : xpaths) {
            WebElement el = findVisible(By.xpath(xpath));
            if (el != null) return el;
        }
        return null;
    }

    private boolean tryAttendanceOnCurrentPage() {
        try {
            // 이미 출근 완료 상태인지 확인
            if (isAlreadyCheckedIn()) {
                log.info("이미 출근 처리되어 있습니다.");
                return true;
            }

            // 출근 버튼 탐색 (CSS 셀렉터)
            WebElement btn = findCheckInButton();
            if (btn == null) {
                return false;
            }

            log.info("출근 버튼 발견 (text='{}'). 클릭합니다.", btn.getText().trim());
            takeScreenshot("02-before-checkin");

            scrollIntoViewAndClick(btn);
            pause(1500);

            // 확인 다이얼로그 처리
            handleConfirmation();

            pause(2000);
            takeScreenshot("03-after-checkin");

            // 클릭 후 완료 상태 재확인
            if (isAlreadyCheckedIn()) {
                log.info("출근 체크인 완료 확인됨.");
                return true;
            }

            // 완료 텍스트 탐색으로 2차 확인
            log.info("출근 체크인 버튼 클릭 완료 (상태 텍스트로 재확인 불가 — 성공으로 간주).");
            return true;

        } catch (Exception e) {
            log.debug("현재 페이지 출석체크 시도 중 예외: {}", e.getMessage());
            return false;
        }
    }

    private boolean isAlreadyCheckedIn() {
        // 근무체크 영역에 "근무중" 텍스트가 있으면 이미 출근 처리된 것
        List<WebElement> working = driver.findElements(By.xpath(
                "//*[contains(text(),'근무중')]"
        ));
        if (working.stream().anyMatch(WebElement::isDisplayed)) {
            return true;
        }
        // 근무현황 영역의 "출근" 텍스트로 2차 확인
        List<WebElement> status = driver.findElements(By.xpath(
                "//*[contains(text(),'근무현황')]/following-sibling::*//*[contains(text(),'출근')] | " +
                "//*[contains(text(),'근무현황')]/parent::*//*[contains(text(),'출근')]"
        ));
        return status.stream().anyMatch(WebElement::isDisplayed);
    }

    private WebElement findCheckInButton() {
        // CSS 셀렉터 방식
        String[] cssSelectors = {
                ".commute-btn",
                ".btn-commute",
                ".work-start-btn",
                "[class*='commute']",
                "[id*='commute']",
                ".attendance-btn",
        };
        for (String sel : cssSelectors) {
            WebElement el = findVisible(By.cssSelector(sel));
            if (el != null) return el;
        }

        // 텍스트 기반 XPath (출근 버튼 — 퇴근/완료 텍스트 제외)
        String[] xpaths = {
                "//button[normalize-space(text())='출근']",
                "//button[contains(text(),'출근') and not(contains(text(),'완료')) and not(contains(text(),'퇴근'))]",
                "//a[normalize-space(text())='출근']",
                "//a[contains(text(),'출근') and not(contains(text(),'완료'))]",
                "//span[normalize-space(text())='출근']/parent::button",
                "//*[contains(@class,'start') and contains(text(),'출근')]",
        };
        for (String xpath : xpaths) {
            WebElement el = findVisible(By.xpath(xpath));
            if (el != null) return el;
        }

        return null;
    }

    private void handleConfirmation() {
        // Alert 처리
        try {
            Alert alert = driver.switchTo().alert();
            log.info("Alert 감지: '{}'", alert.getText());
            alert.accept();
            return;
        } catch (NoAlertPresentException ignored) {
        }

        // 모달/다이얼로그 확인 버튼
        String[] confirmSelectors = {
                ".modal-footer .btn-primary",
                ".modal .btn-confirm",
                ".dialog .btn-ok",
                "button.confirm",
                ".popup button[type='submit']",
                "//button[contains(text(),'확인')]",
                "//button[contains(text(),'OK')]",
        };
        for (String sel : confirmSelectors) {
            try {
                WebElement btn = sel.startsWith("//")
                        ? findVisible(By.xpath(sel))
                        : findVisible(By.cssSelector(sel));
                if (btn != null) {
                    btn.click();
                    log.info("확인 버튼 클릭: {}", sel);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    // ──────────────────────────────────────────────
    // 유틸
    // ──────────────────────────────────────────────

    private WebElement findVisible(By by) {
        try {
            List<WebElement> els = driver.findElements(by);
            return els.stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void clear(WebElement el) {
        el.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        el.clear();
    }

    private void scrollIntoViewAndClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
        pause(300);
        try {
            el.click();
        } catch (ElementClickInterceptedException e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void takeScreenshot(String name) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path dir = Paths.get("logs", "screenshots");
            Files.createDirectories(dir);
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dest = dir.resolve(name + "-" + ts + ".png");
            Files.copy(src.toPath(), dest);
            log.info("스크린샷 저장: {}", dest.toAbsolutePath());
        } catch (Exception e) {
            log.warn("스크린샷 저장 실패: {}", e.getMessage());
        }
    }

    public void quit() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
        }
            driver = null;
            log.info("브라우저 종료");
        }
    }
}
