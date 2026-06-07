package com.eauction.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = "com.eauction")
@EntityScan(basePackages = "com.eauction")
@EnableJpaRepositories(basePackages = "com.eauction")
@EnableTransactionManagement
@EnableScheduling
@EnableConfigurationProperties
public class EAuctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(EAuctionApplication.class, args);
    }
}
