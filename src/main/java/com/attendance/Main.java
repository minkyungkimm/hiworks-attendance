package com.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        log.info("하이웍스 자동 출석체크 프로그램 시작");

        AppConfig config = AppConfig.load();

        if (args.length > 0) {
            switch (args[0]) {
                case "--now":
                case "--checkin":
                    log.info("[출근 모드] 출근 체크를 실행합니다.");
                    runCheckin(config);
                    return;
                case "--checkout":
                    log.info("[퇴근 모드] 퇴근 체크를 실행합니다.");
                    runCheckout(config);
                    return;
                case "--halfday-checkout":
                    log.info("[오후 반차] 반차 퇴근 체크를 실행합니다.");
                    runHalfdayCheckout(config);
                    return;
                case "--vacation-check":
                    log.info("[연차 확인] 전자결재 기안 목록에서 연차 날짜를 조회합니다.");
                    runVacationCheck(config);
                    return;
            }
        }

        // 일반 모드: Quartz 스케줄러로 지정 시간에 실행
        SchedulerService scheduler = new SchedulerService(config);
        scheduler.start();

        log.info("스케줄러 실행 중... 종료하려면 Ctrl+C 를 누르세요.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("프로그램 종료 중...");
            scheduler.stop();
        }));

        Thread.currentThread().join();
    }

    private static void runVacationCheck(AppConfig config) {
        HiworksService service = new HiworksService(config);
        try {
            service.login();
            VacationChecker.Result result = service.fetchVacationDates();
            java.time.LocalDate today = java.time.LocalDate.now();

            log.info("=== 연차(종일) {}개 ===", result.fullDays.size());
            result.fullDays.stream().sorted().forEach(d -> log.info("  [연차] {}", d));

            log.info("=== 반차 {}개 ===", result.halfDays.size());
            result.halfDays.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(e -> log.info("  [{}] {}", e.getValue(), e.getKey()));

            AttendanceState.Decision todayHalf = result.halfDays.get(today);
            log.info("오늘({}) 연차: {} / 반차: {}",
                    today,
                    result.fullDays.contains(today),
                    todayHalf != null ? todayHalf : "없음");
        } catch (Exception e) {
            log.error("오류 발생: {}", e.getMessage(), e);
        } finally {
            service.quit();
        }
    }

    private static void runCheckin(AppConfig config) {
        HiworksService service = new HiworksService(config);
        try {
            service.login();
            boolean done = service.checkAndDoAttendance();
            if (done) {
                log.info("출근 체크 완료!");
            } else {
                log.warn("출근 체크 실패 — logs/screenshots 폴더를 확인하세요.");
            }
        } catch (Exception e) {
            log.error("오류 발생: {}", e.getMessage(), e);
        } finally {
            service.quit();
        }
    }

    private static void runHalfdayCheckout(AppConfig config) {
        HiworksService service = new HiworksService(config);
        try {
            service.login();
            boolean done = service.checkAndDoCheckout(0);
            if (done) {
                log.info("오후 반차 퇴근 체크 완료!");
            } else {
                log.warn("오후 반차 퇴근 체크 실패 — logs/screenshots 폴더를 확인하세요.");
            }
        } catch (Exception e) {
            log.error("오류 발생: {}", e.getMessage(), e);
        } finally {
            service.quit();
        }
    }

    private static void runCheckout(AppConfig config) {
        HiworksService service = new HiworksService(config);
        try {
            service.login();
            boolean done = service.checkAndDoCheckout();
            if (done) {
                log.info("퇴근 체크 완료!");
            } else {
                log.warn("퇴근 체크 실패 — logs/screenshots 폴더를 확인하세요.");
            }
        } catch (Exception e) {
            log.error("오류 발생: {}", e.getMessage(), e);
        } finally {
            service.quit();
        }
    }
}
