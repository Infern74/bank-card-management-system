package com.example.bankcards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankCardSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankCardSystemApplication.class, args);
    }
}