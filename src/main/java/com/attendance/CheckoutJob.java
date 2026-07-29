package com.attendance;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@DisallowConcurrentExecution
public class CheckoutJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CheckoutJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (HolidayChecker.isHoliday(LocalDate.now())) {
            log.info("===== 오늘은 공휴일 - 퇴근 체크 건너뜀 =====");
            return;
        }

        if (AttendanceState.get() == AttendanceState.Decision.VACATION) {
            log.info("===== 오늘은 연차 - 퇴근 체크 건너뜀 =====");
            return;
        }

        AppConfig config = (AppConfig) context.getJobDetail().getJobDataMap().get("config");
        int scheduledHour = context.getJobDetail().getJobDataMap().getIntValue("scheduledHour");
        AttendanceState.Decision state = AttendanceState.get();

        // 오후반차 잡(12, 14시): 오후반차(HALFDAY_8/9) 상태에서만 실행
        if ((scheduledHour == 12 || scheduledHour == 14) && !isAfternoonHalfday(state)) {
            log.info("===== 오후반차 미선택 — {}시 반차 퇴근 잡 건너뜀 (상태: {}) =====", scheduledHour, state);
            return;
        }
        // 정규 퇴근 잡(17, 18시): 오후반차 선택 시 건너뜀
        if ((scheduledHour == 17 || scheduledHour == 18) && isAfternoonHalfday(state)) {
            log.info("===== 오후반차로 {}시 정규 퇴근 잡 건너뜀 =====", scheduledHour);
            return;
        }
        // 정규 출근 라우팅: CHECKIN_8 → 17시, CHECKIN_9 → 18시
        if (scheduledHour == 18 && state == AttendanceState.Decision.CHECKIN_8) {
            log.info("===== CHECKIN_8 — 18시 잡 건너뜀 (17시에 퇴근) =====");
            return;
        }
        if (scheduledHour == 17 && state == AttendanceState.Decision.CHECKIN_9) {
            log.info("===== CHECKIN_9 — 17시 잡 건너뜀 (18시에 퇴근) =====");
            return;
        }
        // 오전반차 라우팅: MORNING_HALFDAY_8 → 17시, MORNING_HALFDAY_9 → 18시
        if (scheduledHour == 17 && state == AttendanceState.Decision.MORNING_HALFDAY_9) {
            log.info("===== 오전반차(9시) — 17시 잡 건너뜀, 18시에 퇴근 =====");
            return;
        }
        if (scheduledHour == 18 && state == AttendanceState.Decision.MORNING_HALFDAY_8) {
            log.info("===== 오전반차(8시) — 18시 잡 건너뜀, 17시에 퇴근 =====");
            return;
        }

        boolean isMorningHalfday = state == AttendanceState.Decision.MORNING_HALFDAY_8
                || state == AttendanceState.Decision.MORNING_HALFDAY_9;
        log.info("===== 퇴근 체크 작업 시작 ({}시 스케줄, 상태: {}) =====", scheduledHour, state);

        int maxRetry = 3;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            HiworksService service = new HiworksService(config);
            try {
                service.login();
                boolean done = service.checkAndDoCheckout(scheduledHour, isMorningHalfday);
                if (done) {
                    log.info("===== 퇴근 체크 완료 (시도 {}/{}) =====", attempt, maxRetry);
                    TelegramBotService bot = TelegramBotService.getInstance();
                    if (bot != null) bot.sendMessage("✅ 퇴근 체크 완료!");
                    return;
                }
                log.warn("퇴근 체크 미완료 (시도 {}/{})", attempt, maxRetry);
            } catch (Exception e) {
                log.error("퇴근 체크 실패 (시도 {}/{}): {}", attempt, maxRetry, e.getMessage(), e);
            } finally {
                service.quit();
            }

            if (attempt < maxRetry) {
                log.info("30초 후 재시도...");
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        log.error("===== 최대 재시도({}) 초과. 퇴근 체크 최종 실패 =====", maxRetry);
    }

    private static boolean isAfternoonHalfday(AttendanceState.Decision d) {
        return d == AttendanceState.Decision.HALFDAY_8 || d == AttendanceState.Decision.HALFDAY_9;
    }
}
