package com.chaptime.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChaptimeBackend1Application {

    // --- LOGGER HINZUFÜGEN ---
    private static final Logger logger = LoggerFactory.getLogger(ChaptimeBackend1Application.class);

    public static void main(String[] args) {
        // --- LOG-MELDUNG HINZUFÜGEN ---
        logger.info("<<<<<<<<< DEPLOYMENT TEST v3.07 - ANWENDUNG STARTET >>>>>>>>>");
        SpringApplication.run(ChaptimeBackend1Application.class, args);
    }
}