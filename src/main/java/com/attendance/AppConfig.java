package com.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * config.properties 파일을 읽어서 설정값을 제공하는 클래스
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "config.properties";

    private final String hiworksUrl;
    private final String company;
    private final String username;
    private final String password;
    private final String scheduleCron;
    private final int retryInterval;
    private final int retryMax;
    private final boolean headless;

    private AppConfig(Properties props) {
        this.hiworksUrl = props.getProperty("hiworks.url");
        this.company = props.getProperty("hiworks.company");
        this.username = props.getProperty("hiworks.username");
        this.password = props.getProperty("hiworks.password");
        this.scheduleCron = props.getProperty("schedule.cron");
        this.retryInterval = Integer.parseInt(props.getProperty("schedule.retry.interval", "60"));
        this.retryMax = Integer.parseInt(props.getProperty("schedule.retry.max", "5"));
        this.headless = Boolean.parseBoolean(props.getProperty("browser.headless", "false"));
    }

    /**
     * config.properties 파일을 로드해서 AppConfig 객체를 생성한다.
     */
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
    public String getScheduleCron() { return scheduleCron; }
    public int getRetryInterval() { return retryInterval; }
    public int getRetryMax() { return retryMax; }
    public boolean isHeadless() { return headless; }
}
