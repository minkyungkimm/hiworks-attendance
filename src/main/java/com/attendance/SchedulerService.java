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
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "HiworksAttendanceScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "1");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        scheduler = new StdSchedulerFactory(props).getScheduler();

        JobDataMap jobData = new JobDataMap();
        jobData.put("config", config);

        JobDetail job = JobBuilder.newJob(AttendanceJob.class)
                .withIdentity("attendanceJob", "hiworks")
                .usingJobData(jobData)
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("attendanceTrigger", "hiworks")
                .withSchedule(CronScheduleBuilder.cronSchedule(config.getScheduleCron()))
                .build();

        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        log.info("스케줄러 시작. Cron: [{}] | 다음 실행: {}", config.getScheduleCron(), trigger.getNextFireTime());
    }

    public void stop() {
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
