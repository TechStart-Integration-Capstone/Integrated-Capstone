package com.bank.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventConsumersApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventConsumersApplication.class, args);
    }
}
