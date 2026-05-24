package com.applypilot.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DebugConfig {
    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @PostConstruct
    public void print() {
        System.out.println("=== DB CONFIG ===");
        System.out.println("URL: " + url);
        System.out.println("USER: " + username);
        System.out.println("PASS: " + password);
        System.out.println("=================");
    }
}
