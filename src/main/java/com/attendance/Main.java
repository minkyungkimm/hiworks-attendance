package com.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 하이웍스 자동 출석체크 프로그램 진입점
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("하이웍스 자동 출석체크 프로그램 시작");

        try {
            AppConfig config = AppConfig.load();
            SchedulerService scheduler = new SchedulerService(config);
            scheduler.start();

            log.info("스케줄러 실행 중... 종료하려면 Ctrl+C 를 누르세요.");

            // 프로그램이 종료되지 않도록 메인 스레드 대기
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("프로그램 종료 중...");
                scheduler.stop();
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("프로그램 실행 중 오류 발생: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
