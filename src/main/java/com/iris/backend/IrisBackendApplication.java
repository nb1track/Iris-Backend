package com.iris.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IrisBackendApplication { // Name geändert
    public static void main(String[] args) {
        SpringApplication.run(IrisBackendApplication.class, args); // Name hier auch anpassen
    }
}