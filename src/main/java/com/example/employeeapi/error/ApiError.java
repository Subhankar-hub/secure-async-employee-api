package com.example.employeeapi.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String code, String message,
                       Map<String, String> details) {

    public static ApiError of(HttpStatus status, String code, String message, Map<String, String> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, details);
    }

    public static ApiError of(HttpStatus status, String code, String message) {
        return of(status, code, message, null);
    }
}
