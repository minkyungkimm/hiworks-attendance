package com.attendance;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class AttendanceJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AttendanceJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        AppConfig config = (AppConfig) context.getJobDetail().getJobDataMap().get("config");

        log.info("===== 출석체크 작업 시작 =====");

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
