package com.attendance;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ShutdownJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("===== 오전 업무 완료 - 프로그램 자동 종료 =====");
        System.exit(0);
    }
}
