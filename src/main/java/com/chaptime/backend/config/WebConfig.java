package com.chaptime.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**") // Gilt für alle Endpunkte unter /api/
                        .allowedOrigins("*") // Erlaubt Anfragen von jeder Herkunft
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Erlaubt alle gängigen Methoden
                        .allowedHeaders("*"); // Erlaubt alle Header
            }
        };
    }
}