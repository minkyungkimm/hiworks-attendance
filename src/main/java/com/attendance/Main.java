package com.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        log.info("하이웍스 자동 출석체크 프로그램 시작");

        AppConfig config = AppConfig.load();

        // --now 플래그: 스케줄 없이 즉시 1회 실행 (테스트용)
        if (args.length > 0 && "--now".equals(args[0])) {
            log.info("[테스트 모드] 즉시 출석체크를 실행합니다.");
            runOnce(config);
            return;
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

    private static void runOnce(AppConfig config) {
        HiworksService service = new HiworksService(config);
        try {
            service.login();
            boolean done = service.checkAndDoAttendance();
            if (done) {
                log.info("출석체크 완료!");
            } else {
                log.warn("출석체크 실패 — logs/screenshots 폴더를 확인하세요.");
            }
        } catch (Exception e) {
            log.error("오류 발생: {}", e.getMessage(), e);
        } finally {
            service.quit();
        }
    }
}
