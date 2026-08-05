package com.tugnw.aistudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiStudyHubBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStudyHubBeApplication.class, args);
    }

}
