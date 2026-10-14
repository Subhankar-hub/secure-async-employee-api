package com.example.employeeapi.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
