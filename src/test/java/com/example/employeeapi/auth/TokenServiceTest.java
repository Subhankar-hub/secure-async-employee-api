package com.example.employeeapi.auth;

import com.example.employeeapi.auth.ClientAuthenticationService.AuthenticatedClient;
import com.example.employeeapi.config.JwtConfig;
import com.example.employeeapi.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "unit-test-signing-secret-that-is-long-enough-123";
    private final JwtConfig jwtConfig = new JwtConfig();
    private final AuthenticatedClient client =
            new AuthenticatedClient("assessment_client", List.of("transactions:write", "transactions:read"));

    private SecurityProperties properties(String secret, String issuer) {
        return new SecurityProperties(new SecurityProperties.Jwt(secret, issuer, 1800), List.of());
    }

    private JwtEncoder encoder(SecurityProperties props) {
        return jwtConfig.jwtEncoder(jwtConfig.jwtSigningKey(props));
    }

    private JwtDecoder decoder(SecurityProperties props) {
        SecretKey key = jwtConfig.jwtSigningKey(props);
        return jwtConfig.jwtDecoder(key, props);
    }

    @Test
    void issuesSignedTokenWithClientIdentityScopesAndExpiry() {
        SecurityProperties props = properties(SECRET, "test-issuer");
        TokenResponse response = new TokenService(encoder(props), props).issue(client);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);

        Jwt jwt = decoder(props).decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("assessment_client");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("transactions:write transactions:read");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(1800));
    }

    @Test
    void rejectsExpiredToken() {
        SecurityProperties props = properties(SECRET, "test-issuer");
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = new TokenService(encoder(props), props, past).issue(client).accessToken();

        assertThatThrownBy(() -> decoder(props).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        SecurityProperties issuing = properties(SECRET, "someone-else");
        String token = new TokenService(encoder(issuing), issuing).issue(client).accessToken();

        assertThatThrownBy(() -> decoder(properties(SECRET, "test-issuer")).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        SecurityProperties other = properties("a-completely-different-signing-secret-xyz-987", "test-issuer");
        String token = new TokenService(encoder(other), other).issue(client).accessToken();

        assertThatThrownBy(() -> decoder(properties(SECRET, "test-issuer")).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsShortSigningSecret() {
        assertThatThrownBy(() -> jwtConfig.jwtSigningKey(properties("too-short", "test-issuer")))
                .isInstanceOf(IllegalStateException.class);
    }
}
