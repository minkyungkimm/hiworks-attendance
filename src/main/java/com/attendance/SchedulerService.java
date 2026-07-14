package com.attendance;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final AppConfig config;
    private Scheduler scheduler;

    public SchedulerService(AppConfig config) {
        this.config = config;
    }

    public void start() throws SchedulerException {
        // 텔레그램 봇 초기화
        if (config.isTelegramConfigured()) {
            TelegramBotService bot = TelegramBotService.create(
                    config.getTelegramBotToken(),
                    config.getTelegramChatId()
            );
            bot.startPolling();
        } else {
            log.warn("텔레그램 설정이 없습니다. config.properties에 telegram.bot.token과 telegram.chat.id를 추가하세요.");
        }

        // Quartz 스케줄러 초기화
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "HiworksAttendanceScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "3");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        scheduler = new StdSchedulerFactory(props).getScheduler();

        // 7:30 텔레그램 알림 잡
        if (config.isTelegramConfigured()) {
            JobDetail reminderJob = JobBuilder.newJob(ReminderJob.class)
                    .withIdentity("reminderJob", "hiworks")
                    .build();
            CronTrigger reminderTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("reminderTrigger", "hiworks")
                    .withSchedule(CronScheduleBuilder.cronSchedule(config.getReminderCron()))
                    .build();
            scheduler.scheduleJob(reminderJob, reminderTrigger);
            log.info("7:30 알림 스케줄 등록: [{}] | 다음 실행: {}",
                    config.getReminderCron(), reminderTrigger.getNextFireTime());
        }

        // 8시 출근 체크 잡
        JobDataMap data8 = new JobDataMap();
        data8.put("config", config);
        data8.put("scheduledHour", 8);

        JobDetail job8 = JobBuilder.newJob(AttendanceJob.class)
                .withIdentity("checkin8Job", "hiworks")
                .usingJobData(data8)
                .build();
        CronTrigger trigger8 = TriggerBuilder.newTrigger()
                .withIdentity("checkin8Trigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getCheckin8Cron()))
                .build();
        scheduler.scheduleJob(job8, trigger8);
        log.info("8시 출근 스케줄 등록: [{}] | 다음 실행: {}",
                config.getCheckin8Cron(), trigger8.getNextFireTime());

        // 9시 출근 체크 잡 (9시 선택 시에만 실행)
        JobDataMap data9 = new JobDataMap();
        data9.put("config", config);
        data9.put("scheduledHour", 9);

        JobDetail job9 = JobBuilder.newJob(AttendanceJob.class)
                .withIdentity("checkin9Job", "hiworks")
                .usingJobData(data9)
                .build();
        CronTrigger trigger9 = TriggerBuilder.newTrigger()
                .withIdentity("checkin9Trigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getCheckin9Cron()))
                .build();
        scheduler.scheduleJob(job9, trigger9);
        log.info("9시 출근 스케줄 등록: [{}] | 다음 실행: {}",
                config.getCheckin9Cron(), trigger9.getNextFireTime());

        // 12:00 반차 퇴근 잡 (8:20 이전 출근자 + 반차 선택 시)
        JobDataMap data12 = new JobDataMap();
        data12.put("config", config);
        data12.put("scheduledHour", 12);

        JobDetail checkout12Job = JobBuilder.newJob(CheckoutJob.class)
                .withIdentity("halfday12Job", "hiworks")
                .usingJobData(data12)
                .build();
        CronTrigger trigger12 = TriggerBuilder.newTrigger()
                .withIdentity("halfday12Trigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getHalfday12Cron()))
                .build();
        scheduler.scheduleJob(checkout12Job, trigger12);
        log.info("12시 반차 퇴근 스케줄 등록: [{}]", config.getHalfday12Cron());

        // 14:00 반차 퇴근 잡 (8:20 이후 출근자 + 반차 선택 시)
        JobDataMap data14 = new JobDataMap();
        data14.put("config", config);
        data14.put("scheduledHour", 14);

        JobDetail checkout14Job = JobBuilder.newJob(CheckoutJob.class)
                .withIdentity("halfday14Job", "hiworks")
                .usingJobData(data14)
                .build();
        CronTrigger trigger14 = TriggerBuilder.newTrigger()
                .withIdentity("halfday14Trigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getHalfday14Cron()))
                .build();
        scheduler.scheduleJob(checkout14Job, trigger14);
        log.info("14시 반차 퇴근 스케줄 등록: [{}]", config.getHalfday14Cron());

        // 17:00 퇴근 잡 (8:20 이전 출근자)
        JobDataMap data17 = new JobDataMap();
        data17.put("config", config);
        data17.put("scheduledHour", 17);

        JobDetail checkout17Job = JobBuilder.newJob(CheckoutJob.class)
                .withIdentity("checkout17Job", "hiworks")
                .usingJobData(data17)
                .build();
        CronTrigger trigger17 = TriggerBuilder.newTrigger()
                .withIdentity("checkout17Trigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getCheckout5Cron()))
                .build();
        scheduler.scheduleJob(checkout17Job, trigger17);
        log.info("5시 퇴근 스케줄 등록: [{}] | 다음 실행: {}",
                config.getCheckout5Cron(), trigger17.getNextFireTime());

        // 18:00 퇴근 잡 (8:20 이후 출근자)
        JobDataMap data18 = new JobDataMap();
        data18.put("config", config);
        data18.put("scheduledHour", 18);

        JobDetail checkout18Job = JobBuilder.newJob(CheckoutJob.class)
                .withIdentity("checkout18Job", "hiworks")
                .usingJobData(data18)
                .build();
        CronTrigger trigger18 = TriggerBuilder.newTrigger()
                .withIdentity("checkout18Trigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getCheckout6Cron()))
                .build();
        scheduler.scheduleJob(checkout18Job, trigger18);
        log.info("6시 퇴근 스케줄 등록: [{}] | 다음 실행: {}",
                config.getCheckout6Cron(), trigger18.getNextFireTime());

        // 18:15 자동 종료 잡
        JobDetail shutdownJob = JobBuilder.newJob(ShutdownJob.class)
                .withIdentity("shutdownJob", "hiworks")
                .build();
        CronTrigger shutdownTrigger = TriggerBuilder.newTrigger()
                .withIdentity("shutdownTrigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getShutdownCron()))
                .build();
        scheduler.scheduleJob(shutdownJob, shutdownTrigger);
        log.info("자동 종료 스케줄 등록: [{}] | 다음 종료: {}",
                config.getShutdownCron(), shutdownTrigger.getNextFireTime());

        scheduler.start();
    }

    public void stop() {
        TelegramBotService bot = TelegramBotService.getInstance();
        if (bot != null) {
            bot.stopPolling();
        }

        if (scheduler != null) {
            try {
                scheduler.shutdown(true);
                log.info("스케줄러 종료 완료");
            } catch (SchedulerException e) {
                log.error("스케줄러 종료 중 오류: {}", e.getMessage(), e);
            }
        }
    }
}
