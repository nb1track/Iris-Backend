package com.iris.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IrisBackendApplication {
    public static void main(String[] args) {
        System.out.println("Test 1");
        SpringApplication.run(IrisBackendApplication.class, args);
    }
}