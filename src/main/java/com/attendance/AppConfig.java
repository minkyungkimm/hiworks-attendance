package com.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "config.properties";

    private final String hiworksUrl;
    private final String company;
    private final String username;
    private final String password;
    private final int retryInterval;
    private final int retryMax;
    private final boolean headless;

    private final String telegramBotToken;
    private final String telegramChatId;
    private final String reminderCron;
    private final String checkin8Cron;
    private final String checkin9Cron;
    private final String halfday12Cron;
    private final String halfday14Cron;
    private final String checkout5Cron;
    private final String checkout6Cron;
    private final String shutdownCron;

    private AppConfig(Properties props) {
        this.hiworksUrl = props.getProperty("hiworks.url");
        this.company = props.getProperty("hiworks.company");
        this.username = props.getProperty("hiworks.username");
        this.password = props.getProperty("hiworks.password");
        this.retryInterval = Integer.parseInt(props.getProperty("schedule.retry.interval", "60"));
        this.retryMax = Integer.parseInt(props.getProperty("schedule.retry.max", "5"));
        this.headless = Boolean.parseBoolean(props.getProperty("browser.headless", "false"));

        this.telegramBotToken = props.getProperty("telegram.bot.token", "");
        this.telegramChatId = props.getProperty("telegram.chat.id", "");
        this.reminderCron = props.getProperty("schedule.reminder.cron", "0 30 7 ? * MON-FRI");
        this.checkin8Cron = props.getProperty("schedule.checkin8.cron", "0 0 8 ? * MON-FRI");
        this.checkin9Cron  = props.getProperty("schedule.checkin9.cron",  "0 0 9 ? * MON-FRI");
        this.halfday12Cron = props.getProperty("schedule.halfday12.cron", "0 0 12 ? * MON-FRI");
        this.halfday14Cron = props.getProperty("schedule.halfday14.cron", "0 0 14 ? * MON-FRI");
        this.checkout5Cron = props.getProperty("schedule.checkout5.cron", "0 0 17 ? * MON-FRI");
        this.checkout6Cron = props.getProperty("schedule.checkout6.cron", "0 0 18 ? * MON-FRI");
        this.shutdownCron  = props.getProperty("schedule.shutdown.cron",  "0 15 18 ? * MON-FRI");
    }

    public static AppConfig load() throws IOException {
        Properties props = new Properties();
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("설정 파일을 찾을 수 없습니다: " + CONFIG_FILE);
            }
            props.load(input);
            log.info("설정 파일 로드 완료");
        }
        return new AppConfig(props);
    }

    public String getHiworksUrl() { return hiworksUrl; }
    public String getCompany() { return company; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getRetryInterval() { return retryInterval; }
    public int getRetryMax() { return retryMax; }
    public boolean isHeadless() { return headless; }

    public String getTelegramBotToken() { return telegramBotToken; }
    public String getTelegramChatId() { return telegramChatId; }
    public String getReminderCron() { return reminderCron; }
    public String getCheckin8Cron() { return checkin8Cron; }
    public String getCheckin9Cron()  { return checkin9Cron; }
    public String getHalfday12Cron() { return halfday12Cron; }
    public String getHalfday14Cron() { return halfday14Cron; }
    public String getCheckout5Cron() { return checkout5Cron; }
    public String getCheckout6Cron() { return checkout6Cron; }
    public String getShutdownCron()  { return shutdownCron; }

    public boolean isTelegramConfigured() {
        return telegramBotToken != null && !telegramBotToken.isEmpty()
                && telegramChatId != null && !telegramChatId.isEmpty();
    }
}
