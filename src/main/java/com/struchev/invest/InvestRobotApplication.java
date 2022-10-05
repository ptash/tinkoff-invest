package com.struchev.invest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableRetry
public class InvestRobotApplication {

    @Value("${invest.timeZone:Europe/Moscow}")
    private String timeZone;

    public static void main(String[] args) {
        SpringApplication.run(InvestRobotApplication.class, args);
    }

    @PostConstruct
    public void init(){
        // Setting Spring Boot SetTimeZone
        if (timeZone != null && !timeZone.isEmpty())
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    }
}
