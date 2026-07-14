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
            if (bot != null) {
                bot.sendMessage("🎉 오늘은 공휴일입니다. 출근 체크를 건너뜁니다.");
            }
            return;
        }

        log.info("===== 출근 알림 발송 =====");
        if (bot == null) {
            log.warn("텔레그램 봇이 설정되지 않았습니다.");
            return;
        }
        bot.sendReminderMessage();
    }
}
