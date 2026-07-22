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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HiworksService {

    private static final Logger log = LoggerFactory.getLogger(HiworksService.class);

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final Duration PAGE_LOAD = Duration.ofSeconds(30);

    private static final String WORK_PAGE_URL = "https://hr-work.office.hiworks.com/personal/index";
    private static final String APPROVAL_BOX_URL = "https://approval.office.hiworks.com/%s/approval/document/box/writer";

    // 8:20 이전 출근 → 17:00 퇴근 / 8:20 이후 출근 → 18:00 퇴근
    private static final LocalTime CHECKIN_CUTOFF  = LocalTime.of(8, 20);
    private static final LocalTime CHECKOUT_5PM    = LocalTime.of(17, 0);
    private static final LocalTime CHECKOUT_6PM    = LocalTime.of(18, 0);

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

        inputCompanyIfPresent();

        WebElement userField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[name='user_id'], #user_id, input[name='id'], input[placeholder*='아이디'], input[placeholder*='ID']")
        ));
        clear(userField);
        userField.sendKeys(config.getUsername());
        log.info("아이디 입력 완료");

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

        WebElement pwField = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[type='password']")
        ));
        clear(pwField);
        pwField.sendKeys(config.getPassword());
        log.info("비밀번호 입력 완료");

        try {
            driver.findElement(By.cssSelector(
                    "button[type='submit'], input[type='submit'], .login-btn, #btnLogin, .btn-login"
            )).click();
        } catch (NoSuchElementException e) {
            pwField.sendKeys(Keys.ENTER);
        }

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

    public VacationChecker.Result fetchVacationDates() {
        String url = String.format(APPROVAL_BOX_URL, config.getCompany());
        return VacationChecker.parse(driver, url);
    }

    public boolean checkAndDoAttendance() {
        return checkAndDoAttendance(null);
    }

    public boolean checkAndDoAttendance(LocalTime targetTime) {
        return checkAndDoAttendance(targetTime, -1);
    }

    public boolean checkAndDoAttendance(LocalTime targetTime, int scheduledHour) {
        log.info("근무 페이지 이동: {}", WORK_PAGE_URL);
        driver.get(WORK_PAGE_URL);
        waitForWorkPageContent();
        takeScreenshot("02-work-page");
        if (targetTime != null) {
            if (!waitUntilTime(targetTime, scheduledHour)) {
                log.info("상태 변경으로 {}시 잡 취소 — 브라우저 종료", scheduledHour);
                return false;
            }
        }
        return tryAttendanceOnCurrentPage();
    }

    // ──────────────────────────────────────────────
    // 퇴근체크
    // ──────────────────────────────────────────────

    // 수동 실행 (run-checkout.bat): 스케줄 시간 체크 없이 동적 퇴근 시간 적용
    public boolean checkAndDoCheckout() {
        return checkAndDoCheckout(-1);
    }

    // 자동 실행 (CheckoutJob): scheduledHour(17 또는 18)에 따라 실행 여부 판단
    public boolean checkAndDoCheckout(int scheduledHour) {
        return checkAndDoCheckout(scheduledHour, false);
    }

    // 오전반차 모드: bypassCheckinTimeCheck=true 시 8:20 기준 체크 건너뜀
    public boolean checkAndDoCheckout(int scheduledHour, boolean bypassCheckinTimeCheck) {
        log.info("근무 페이지 이동 (퇴근): {}", WORK_PAGE_URL);
        driver.get(WORK_PAGE_URL);
        waitForWorkPageContent();
        waitForTimeTextVisible();
        takeScreenshot("02-checkout-page");

        try {
            if (isAlreadyCheckedOut()) {
                log.info("이미 퇴근 처리되어 있습니다.");
                return true;
            }

            if (!isAlreadyCheckedIn()) {
                log.warn("출근 기록이 없어 퇴근 처리를 할 수 없습니다.");
                return false;
            }

            // 출근 시간 읽기
            LocalTime checkinTime = getCheckinTime();

            // 스케줄/모드별 퇴근 시간 결정
            LocalTime targetCheckout;
            if (scheduledHour == 0) {
                // 수동 반차 (run-halfday-checkout.bat)
                targetCheckout = checkinTime != null && checkinTime.isBefore(CHECKIN_CUTOFF)
                        ? LocalTime.of(12, 0)
                        : LocalTime.of(14, 0);
                log.info("오후 반차 수동 모드: 퇴근 가능 시각 {}", targetCheckout);
            } else if (scheduledHour == 12) {
                // 자동 반차 - 8:20 이전 출근자
                if (checkinTime == null || !checkinTime.isBefore(CHECKIN_CUTOFF)) {
                    log.info("12시 반차 조건 미충족 (출근 시간: {}) → 건너뜀", checkinTime);
                    return false;
                }
                targetCheckout = LocalTime.of(12, 0);
            } else if (scheduledHour == 14) {
                // 자동 반차 - 8:20 이후 출근자
                if (checkinTime != null && checkinTime.isBefore(CHECKIN_CUTOFF)) {
                    log.info("14시 반차 조건 미충족 (출근 시간: {} → 12시 반차 대상) → 건너뜀", checkinTime);
                    return false;
                }
                targetCheckout = LocalTime.of(14, 0);
            } else if (bypassCheckinTimeCheck) {
                // 오전반차: 체크인 시간(8:20) 체크 없이 scheduledHour 기준으로 퇴근
                targetCheckout = scheduledHour == 17 ? CHECKOUT_5PM : CHECKOUT_6PM;
                log.info("오전반차 모드 — 퇴근 가능 시각 {}:00 (8:20 기준 체크 생략)", scheduledHour);
            } else {
                targetCheckout = determineCheckoutTime(checkinTime);

                if (scheduledHour == 17) {
                    if (checkinTime == null || !checkinTime.isBefore(CHECKIN_CUTOFF)) {
                        log.info("5시 퇴근 조건 미충족 (출근 시간: {}) → 건너뜀", checkinTime);
                        return false;
                    }
                } else if (scheduledHour == 18) {
                    if (checkinTime != null && checkinTime.isBefore(CHECKIN_CUTOFF)) {
                        log.info("6시 퇴근 조건 미충족 (출근 {}→ 5시 퇴근 대상) → 건너뜀", checkinTime);
                        return false;
                    }
                }
            }

            // 퇴근 시각 1분 후 클릭 (자동 스케줄 모드에서만)
            if (scheduledHour > 0) waitUntilTime(LocalTime.of(scheduledHour, 1, 0));

            // 현재 시각이 퇴근 가능 시간인지 확인
            LocalTime now = LocalTime.now();
            if (now.isBefore(targetCheckout)) {
                log.warn("현재 시각 {}시 {}분 - 퇴근 가능 시각은 {}입니다.",
                        now.getHour(), now.getMinute(), targetCheckout);
                return false;
            }

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
            waitForPageStable();
            takeScreenshot("04-after-checkout");

            if (isAlreadyCheckedOut()) {
                log.info("퇴근 체크 완료 확인됨.");
                return true;
            }

            log.info("퇴근 버튼 클릭 완료 (성공으로 간주).");
            return true;

        } catch (Exception e) {
            log.error("퇴근 처리 중 오류: {}", e.getMessage(), e);
            return false;
        }
    }

    // ──────────────────────────────────────────────
    // 출근 시간 읽기
    // ──────────────────────────────────────────────

    public LocalTime getCheckinTime() {
        try {
            Pattern timePattern = Pattern.compile("\\b([0-1]?[0-9]):([0-5][0-9])\\b");
            String pageText = driver.findElement(By.tagName("body")).getText();
            String[] lines = pageText.split("[\\n\\r]+");

            // "출근하기" 레이블을 기준으로 아래 줄에서 시간 탐색
            // "근무중" 옆의 시간은 현재 시각이므로 건너뜀
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.contains("출근하기")) continue;

                // 출근하기 아래 최대 5줄 탐색
                for (int j = i + 1; j <= Math.min(i + 5, lines.length - 1); j++) {
                    String nearby = lines[j].trim();
                    if (nearby.contains("근무중")) continue; // 현재 시각 표시 줄 건너뜀

                    Matcher m = timePattern.matcher(nearby);
                    while (m.find()) {
                        int hour   = Integer.parseInt(m.group(1));
                        int minute = Integer.parseInt(m.group(2));
                        if (hour >= 5 && hour <= 12) {
                            LocalTime t = LocalTime.of(hour, minute);
                            log.info("출근 시간 감지 (출근하기 섹션): {}", t);
                            return t;
                        }
                    }
                }
            }

            log.warn("출근 시간을 페이지에서 찾지 못했습니다.");
            return null;

        } catch (Exception e) {
            log.error("출근 시간 읽기 중 오류: {}", e.getMessage());
            return null;
        }
    }

    private LocalTime determineCheckoutTime(LocalTime checkinTime) {
        if (checkinTime == null) {
            log.warn("출근 시간 미확인 → 기본값 18:00 적용");
            return CHECKOUT_6PM;
        }
        if (checkinTime.isBefore(CHECKIN_CUTOFF)) {
            log.info("출근 {} → 8:20 이전 → 17:00 퇴근", checkinTime);
            return CHECKOUT_5PM;
        } else {
            log.info("출근 {} → 8:20 이후 → 18:00 퇴근", checkinTime);
            return CHECKOUT_6PM;
        }
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────

    private boolean isAlreadyCheckedOut() {
        // 퇴근하기 버튼이 비활성(disabled)이거나 없으면 이미 퇴근된 상태
        WebElement checkoutBtn = findVisible(By.xpath("//button[contains(.,'퇴근하기') and not(@disabled)]"));
        if (checkoutBtn == null) {
            log.info("퇴근하기 버튼 없음 또는 비활성 → 이미 퇴근 처리됨");
            return true;
        }
        log.info("퇴근하기 버튼 활성 → 퇴근 미완료");
        return false;
    }

    private WebElement findCheckOutButton() {
        return findVisible(By.xpath("//button[contains(.,'퇴근하기') and not(@disabled)]"));
    }

    private void waitForWorkPageContent() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//*[contains(.,'출근하기') or contains(.,'퇴근하기') or contains(.,'근무중')]")
            ));
            log.info("근무 페이지 콘텐츠 로드 완료");
        } catch (Exception e) {
            log.warn("근무 페이지 콘텐츠 로드 대기 시간 초과 — 그대로 진행");
        }
        waitForPageStable();
    }

    // 출근 가능 시간대(05:00~14:59)의 시간 텍스트가 나타날 때까지 대기
    // 현재 시각(15:33 등)은 범위 밖이므로 무시됨
    private void waitForTimeTextVisible() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
                try {
                    String bodyText = d.findElement(By.tagName("body")).getText();
                    Matcher m = Pattern.compile("\\b([0-1]?[0-9]):([0-5][0-9])\\b").matcher(bodyText);
                    while (m.find()) {
                        int hour = Integer.parseInt(m.group(1));
                        if (hour >= 5 && hour <= 14) return true;
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            });
            log.info("출근 시간 텍스트 로드 확인 — 스크린샷 진행");
        } catch (Exception e) {
            log.warn("출근 시간 텍스트 로드 대기 시간 초과 — 그대로 진행");
        }
    }

    private void waitUntilTime(LocalTime target) {
        waitUntilTime(target, -1);
    }

    private boolean waitUntilTime(LocalTime target, int scheduledHour) {
        LocalTime now = LocalTime.now();
        if (!now.isBefore(target)) return true;

        long totalMillis = java.time.Duration.between(now, target).toMillis();
        log.info("정각 클릭 대기 중... {} 까지 {}초 남음", target, totalMillis / 1000);

        long deadline = System.currentTimeMillis() + totalMillis;
        while (System.currentTimeMillis() < deadline) {
            if (scheduledHour == 8) {
                AttendanceState.Decision state = AttendanceState.get();
                if (state == AttendanceState.Decision.CHECKIN_9
                        || state == AttendanceState.Decision.HALFDAY_9) {
                    log.info("대기 중 {}로 변경됨 — 8시 잡 취소, 9시 잡에 양보", state);
                    return false;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    // body가 실제 내용을 갖고, 로딩 인디케이터가 사라질 때까지 대기
    private void waitForPageStable() {
        // 1. body 텍스트가 충분히 채워질 때까지 대기 (빈 화면/초기 렌더링 방지)
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                try {
                    return d.findElement(By.tagName("body")).getText().trim().length() > 50;
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            log.debug("페이지 본문 로드 대기 시간 초과");
        }

        // 2. 로딩 스피너/오버레이가 사라질 때까지 대기 (최대 8초)
        try {
            new WebDriverWait(driver, Duration.ofSeconds(8)).until(d -> {
                try {
                    List<WebElement> loaders = d.findElements(By.cssSelector(
                            "[class*='loading'], [class*='spinner'], [class*='skeleton'], " +
                            "[class*='progress'], .dim, .overlay, .loader"
                    ));
                    return loaders.stream().noneMatch(WebElement::isDisplayed);
                } catch (Exception e) {
                    return true;
                }
            });
        } catch (Exception ignored) {
            // 시간 초과 시 그냥 진행
        }
    }

    private boolean tryAttendanceOnCurrentPage() {
        try {
            if (isAlreadyCheckedIn()) {
                log.info("이미 출근 처리되어 있습니다.");
                return true;
            }

            WebElement btn = findCheckInButton();
            if (btn == null) {
                return false;
            }

            log.info("출근 버튼 발견 (text='{}'). 클릭합니다.", btn.getText().trim());
            takeScreenshot("02-before-checkin");

            scrollIntoViewAndClick(btn);
            pause(1500);
            handleConfirmation();
            waitForPageStable();
            takeScreenshot("03-after-checkin");

            if (isAlreadyCheckedIn()) {
                log.info("출근 체크인 완료 확인됨.");
                return true;
            }

            log.info("출근 체크인 버튼 클릭 완료 (성공으로 간주).");
            return true;

        } catch (Exception e) {
            log.debug("현재 페이지 출석체크 시도 중 예외: {}", e.getMessage());
            return false;
        }
    }

    private boolean isAlreadyCheckedIn() {
        // 출근하기 버튼이 비활성(disabled)이거나 없으면 이미 출근된 상태
        WebElement checkinBtn = findVisible(By.xpath("//button[contains(.,'출근하기') and not(@disabled)]"));
        if (checkinBtn == null) {
            log.info("출근하기 버튼 없음 또는 비활성 → 이미 출근 처리됨");
            return true;
        }
        log.info("출근하기 버튼 활성 → 출근 미완료");
        return false;
    }

    private WebElement findCheckInButton() {
        return findVisible(By.xpath("//button[contains(.,'출근하기') and not(@disabled)]"));
    }

    private void handleConfirmation() {
        try {
            Alert alert = driver.switchTo().alert();
            log.info("Alert 감지: '{}'", alert.getText());
            alert.accept();
            return;
        } catch (NoAlertPresentException ignored) {
        }

        String[] confirmSelectors = {
                ".modal-footer .btn-primary", ".modal .btn-confirm",
                ".dialog .btn-ok", "button.confirm", ".popup button[type='submit']",
                "//button[contains(text(),'확인')]", "//button[contains(text(),'OK')]",
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
            cleanupOldScreenshots(dir);
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path dest = dir.resolve(name + "-" + ts + ".png");
            Files.copy(src.toPath(), dest);
            log.info("스크린샷 저장: {}", dest.toAbsolutePath());
        } catch (Exception e) {
            log.warn("스크린샷 저장 실패: {}", e.getMessage());
        }
    }

    private void cleanupOldScreenshots(Path dir) {
        try {
            long cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".png"))
                        .filter(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis() < cutoff;
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                                log.debug("오래된 스크린샷 삭제: {}", p.getFileName());
                            } catch (Exception e) {
                                log.warn("스크린샷 삭제 실패: {}", p.getFileName());
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("스크린샷 정리 중 오류: {}", e.getMessage());
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
