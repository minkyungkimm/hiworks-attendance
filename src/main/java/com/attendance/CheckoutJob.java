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

        // 반차 잡(12, 14시): 반차(HALFDAY_8 또는 HALFDAY_9) 선택 시에만 실행
        if ((scheduledHour == 12 || scheduledHour == 14) && !isHalfday(state)) {
            log.info("===== 반차가 선택되지 않아 {}시 퇴근 잡 건너뜀 =====", scheduledHour);
            return;
        }
        // 정규 퇴근 잡(17, 18시): 반차 선택 시 건너뜀
        if ((scheduledHour == 17 || scheduledHour == 18) && isHalfday(state)) {
            log.info("===== 오후 반차 선택으로 {}시 정규 퇴근 잡 건너뜀 =====", scheduledHour);
            return;
        }

        log.info("===== 퇴근 체크 작업 시작 ({}시 스케줄, 상태: {}) =====", scheduledHour, state);

        HiworksService service = new HiworksService(config);
        try {
            service.login();
            boolean done = service.checkAndDoCheckout(scheduledHour);
            if (done) {
                log.info("===== 퇴근 체크 완료 ({}시 스케줄) =====", scheduledHour);
            }
        } catch (Exception e) {
            log.error("퇴근 체크 실패: {}", e.getMessage(), e);
        } finally {
            service.quit();
        }
    }

    private static boolean isHalfday(AttendanceState.Decision d) {
        return d == AttendanceState.Decision.HALFDAY_8 || d == AttendanceState.Decision.HALFDAY_9;
    }
}
