package com.example.employeeapi.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Set<String> REQUIRED_CODES = Set.of("NotNull", "NotBlank");
    private static final PropertyNamingStrategies.SnakeCaseStrategy SNAKE_CASE =
            new PropertyNamingStrategies.SnakeCaseStrategy();

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        return respond(e.getStatus(), e.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        // Report one message per field, preferring "required" over format errors.
        Map<String, String> details = e.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing((FieldError f) -> !REQUIRED_CODES.contains(f.getCode())))
                .collect(Collectors.toMap(f -> SNAKE_CASE.translate(f.getField()),
                        f -> f.getDefaultMessage() == null ? "Invalid value" : f.getDefaultMessage(),
                        (first, ignored) -> first, LinkedHashMap::new));
        String message = "employeeRequest".equals(e.getBindingResult().getObjectName())
                ? "Employee validation failed" : "Request validation failed";
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        Map<String, String> details = null;
        if (e.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = mapping.getPath().get(mapping.getPath().size() - 1).getFieldName();
            if (field != null) {
                details = Map.of(field, "dob".equals(field)
                        ? "Date of birth must be a valid calendar date in YYYY-MM-DD format"
                        : "Invalid value or type");
            }
        }
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed or invalid request body", details);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiError> handleLocking(RuntimeException e) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The transaction was modified concurrently; please retry", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        return respond(HttpStatus.CONFLICT, "DATA_CONFLICT", "Request conflicts with existing data", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method not supported", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content type must be application/json", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        // Exception messages may contain PII or internal details, so only the type is logged.
        log.error("Unexpected error: {}", e.getClass().getName());
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String code, String message,
                                                    Map<String, String> details) {
        return ResponseEntity.status(status).body(ApiError.of(status, code, message, details));
    }
}
