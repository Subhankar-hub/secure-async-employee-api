package com.example.employeeapi.auth;

import com.example.employeeapi.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ClientAuthenticationService clientAuthenticationService;
    private final TokenService tokenService;

    public AuthController(ClientAuthenticationService clientAuthenticationService, TokenService tokenService) {
        this.clientAuthenticationService = clientAuthenticationService;
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> issueToken(@Valid @RequestBody TokenRequest request) {
        TokenResponse response = clientAuthenticationService
                .authenticate(request.clientId(), request.clientSecret())
                .map(tokenService::issue)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CLIENT",
                        "Invalid client credentials"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
