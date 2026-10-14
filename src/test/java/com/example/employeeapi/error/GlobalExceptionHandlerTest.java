package com.example.employeeapi.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorsReturnGenericBodyWithoutInternalDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("password=hunter2 at /srv/app/internal.sql"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        assertThat(body.toString()).doesNotContain("hunter2").doesNotContain("/srv/app");
        assertThat(body.details()).isNull();
    }

    @Test
    void apiExceptionsKeepStatusCodeAndMessage() {
        ResponseEntity<ApiError> response = handler.handleApi(ApiException.notFound());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("TRANSACTION_NOT_FOUND");
    }
}
