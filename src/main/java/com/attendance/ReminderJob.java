package com.attendance;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class ReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TelegramBotService bot = TelegramBotService.getInstance();

        if (HolidayChecker.isHoliday(LocalDate.now())) {
            log.info("===== 오늘은 공휴일 - 출근 알림 건너뜀 =====");
            if (bot != null) bot.sendMessage("🎉 오늘은 공휴일입니다. 출근 체크를 건너뜁니다.");
            return;
        }

        // 전자결재 기안 목록에서 오늘 연차 여부 확인
        AppConfig config = (AppConfig) context.getJobDetail().getJobDataMap().get("config");
        if (config != null && checkApprovalVacation(config, bot)) {
            return; // 연차 감지 시 버튼 메시지 생략
        }

        log.info("===== 출근 알림 발송 =====");
        if (bot == null) {
            log.warn("텔레그램 봇이 설정되지 않았습니다.");
            return;
        }
        bot.sendReminderMessage();
    }

    /**
     * @return true이면 버튼 메시지 생략 (연차 또는 반차 감지), false이면 버튼 메시지 발송
     */
    private boolean checkApprovalVacation(AppConfig config, TelegramBotService bot) {
        HiworksService service = new HiworksService(config);
        try {
            service.login();
            VacationChecker.Result result = service.fetchVacationDates();
            LocalDate today = LocalDate.now();

            if (result.fullDays.contains(today)) {
                log.info("===== 전자결재에서 오늘({}) 연차 확인 — VACATION 자동 설정 =====", today);
                AttendanceState.set(AttendanceState.Decision.VACATION);
                if (bot != null) {
                    bot.sendMessage("🎉 전자결재에서 오늘 연차가 확인되었습니다. 출퇴근 체크를 자동으로 건너뜁니다.");
                }
                return true; // 버튼 메시지 생략
            }

            AttendanceState.Decision halfDayState = result.halfDays.get(today);
            if (halfDayState != null) {
                AttendanceState.set(halfDayState);
                log.info("===== 전자결재에서 오늘({}) 반차 확인 — {} 자동 설정 =====", today, halfDayState);
                if (bot != null) bot.sendMessage(buildHalfDayMessage(halfDayState));
                return true; // 버튼 메시지 생략
            }

            log.info("전자결재에서 오늘 연차/반차 없음 — 정상 진행");
        } catch (Exception e) {
            log.warn("전자결재 연차 확인 실패 (정상 진행): {}", e.getMessage());
        } finally {
            service.quit();
        }
        return false;
    }

    private static String buildHalfDayMessage(AttendanceState.Decision state) {
        switch (state) {
            case MORNING_HALFDAY_8:
                return "🌄 전자결재에서 오늘 오전반차가 확인되었습니다.\n"
                        + "1시 출근 / 17시 퇴근으로 자동 설정됩니다.";
            case MORNING_HALFDAY_9:
                return "🌄 전자결재에서 오늘 오전반차가 확인되었습니다.\n"
                        + "2시 출근 / 18시 퇴근으로 자동 설정됩니다.";
            case HALFDAY_8:
                return "🌅 전자결재에서 오늘 오후반차가 확인되었습니다.\n"
                        + "8시 출근 / 12시 퇴근으로 자동 설정됩니다.";
            case HALFDAY_9:
                return "🌅 전자결재에서 오늘 오후반차가 확인되었습니다.\n"
                        + "9시 출근 / 14시 퇴근으로 자동 설정됩니다.";
            default:
                return "🌅 전자결재에서 오늘 반차가 확인되었습니다.";
        }
    }
}
