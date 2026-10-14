package com.example.employeeapi.auth;

import com.example.employeeapi.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAuthenticationServiceTest {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);

    private final ClientAuthenticationService service = new ClientAuthenticationService(new SecurityProperties(
            new SecurityProperties.Jwt("unused", "issuer", 1800),
            List.of(
                    new SecurityProperties.Client("prefixed", "{bcrypt}" + BCRYPT.encode("secret-one"),
                            List.of("transactions:read")),
                    new SecurityProperties.Client("plain", BCRYPT.encode("secret-two"), List.of()),
                    new SecurityProperties.Client("disabled", "", List.of("transactions:read")))));

    @Test
    void acceptsValidCredentialsAndReturnsScopes() {
        assertThat(service.authenticate("prefixed", "secret-one"))
                .hasValueSatisfying(c -> {
                    assertThat(c.clientId()).isEqualTo("prefixed");
                    assertThat(c.scopes()).containsExactly("transactions:read");
                });
        assertThat(service.authenticate("plain", "secret-two")).isPresent();
    }

    @Test
    void rejectsWrongSecretUnknownClientAndClientWithoutConfiguredSecret() {
        assertThat(service.authenticate("prefixed", "wrong")).isEmpty();
        assertThat(service.authenticate("unknown", "secret-one")).isEmpty();
        assertThat(service.authenticate("disabled", "")).isEmpty();
    }
}
