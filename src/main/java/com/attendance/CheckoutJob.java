package com.attendance;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class CheckoutJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CheckoutJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        AppConfig config = (AppConfig) context.getJobDetail().getJobDataMap().get("config");
        int scheduledHour = context.getJobDetail().getJobDataMap().getIntValue("scheduledHour");

        log.info("===== 퇴근 체크 작업 시작 ({}시 스케줄) =====", scheduledHour);

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
}
