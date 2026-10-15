package com.example.employeeapi.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @Schema(example = "assessment_client")
        @NotBlank(message = "client_id is required") @Size(max = 100, message = "client_id is too long")
        String clientId,
        @Schema(example = "your-plaintext-secret")
        @NotBlank(message = "client_secret is required") @Size(max = 256, message = "client_secret is too long")
        String clientSecret) {

    @Override
    public String toString() {
        return "TokenRequest[clientId=" + clientId + ", clientSecret=****]";
    }
}
