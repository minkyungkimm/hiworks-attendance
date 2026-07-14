package com.attendance;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("===== 출근 알림 발송 =====");
        TelegramBotService bot = TelegramBotService.getInstance();
        if (bot == null) {
            log.warn("텔레그램 봇이 설정되지 않았습니다.");
            return;
        }
        bot.sendReminderMessage();
    }
}
