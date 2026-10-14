package com.example.employeeapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app.security")
public record SecurityProperties(Jwt jwt, List<Client> clients) {

    public record Jwt(String secret, String issuer, long ttlSeconds) {
    }

    public record Client(String clientId, String secretHash, List<String> scopes) {
    }
}
