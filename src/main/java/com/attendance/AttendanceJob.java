package com.attendance;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@DisallowConcurrentExecution
public class AttendanceJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AttendanceJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (HolidayChecker.isHoliday(LocalDate.now())) {
            log.info("===== 오늘은 공휴일 - 출근 체크 건너뜀 =====");
            return;
        }

        if (AttendanceState.get() == AttendanceState.Decision.VACATION) {
            log.info("===== 오늘은 연차 - 출근 체크 건너뜀 =====");
            return;
        }

        AppConfig config = (AppConfig) context.getJobDetail().getJobDataMap().get("config");
        int scheduledHour = context.getJobDetail().getJobDataMap().containsKey("scheduledHour")
                ? context.getJobDetail().getJobDataMap().getIntValue("scheduledHour")
                : -1;

        // 텔레그램 응답 기반으로 실행 여부 결정
        if (scheduledHour == 8 || scheduledHour == 9) {
            AttendanceState.Decision decision = AttendanceState.get();
            boolean isNineAM = (decision == AttendanceState.Decision.CHECKIN_9
                    || decision == AttendanceState.Decision.HALFDAY_9);
            if (scheduledHour == 8 && isNineAM) {
                log.info("===== 8시 스케줄러 건너뜀 (9시 출근으로 설정됨: {}) =====", decision);
                return;
            }
            if (scheduledHour == 9 && !isNineAM) {
                log.info("===== 9시 스케줄러 건너뜀 (현재 상태: {}) =====", decision);
                return;
            }
            log.info("===== 출석체크 작업 시작 ({}시 스케줄, 상태: {}) =====", scheduledHour, decision);
        } else {
            log.info("===== 출석체크 작업 시작 =====");
        }

        int attempt = 0;
        while (attempt < config.getRetryMax()) {
            attempt++;
            HiworksService service = new HiworksService(config);
            try {
                service.login();
                boolean done = service.checkAndDoAttendance();
                if (done) {
                    log.info("===== 출석체크 완료 (시도 {}/{}) =====", attempt, config.getRetryMax());
                    return;
                }
                log.warn("출석체크 미완료 (시도 {}/{})", attempt, config.getRetryMax());
            } catch (Exception e) {
                log.error("출석체크 실패 (시도 {}/{}): {}", attempt, config.getRetryMax(), e.getMessage(), e);
            } finally {
                service.quit();
            }

            if (attempt < config.getRetryMax()) {
                log.info("{}초 후 재시도...", config.getRetryInterval());
                try {
                    Thread.sleep(config.getRetryInterval() * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.error("===== 최대 재시도({}) 초과. 출석체크 최종 실패 =====", config.getRetryMax());
    }
}
