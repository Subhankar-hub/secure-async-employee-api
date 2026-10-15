package com.example.employeeapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIT extends AbstractIntegrationTest {

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void validCredentialsIssueBearerTokenWith1800SecondExpiry() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("client_id", CLIENT_A, "client_secret", SECRET_A))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(1800))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(SECRET_A);
    }

    @Test
    void invalidCredentialsAreRejectedWith401() throws Exception {
        for (Map<String, String> credentials : new Map[]{
                Map.of("client_id", CLIENT_A, "client_secret", "wrong-secret"),
                Map.of("client_id", "unknown_client", "client_secret", SECRET_A),
                Map.of("client_id", CLIENT_A, "client_secret", SECRET_B)}) {
            String body = mockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(credentials)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid client credentials"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(credentials.get("client_secret"));
        }
    }

    @Test
    void missingTokenRequestFieldsAreRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"assessment_client\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.client_secret").exists());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/init"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/v1/transactions/TXN-0000000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer not-a-jwt", "Bearer a.b.c", "Bearer ", "Basic Zm9vOmJhcg==", "Token abc"})
    void malformedTokensAreRejected(String authorization) throws Exception {
        mockMvc.perform(post("/api/v1/transactions/init").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant issued = Instant.now().minusSeconds(7200);
        String token = encode(jwtEncoder, claims(ISSUER, issued, issued.plusSeconds(1800)));
        mockMvc.perform(post("/api/v1/transactions/init").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws Exception {
        Instant now = Instant.now();
        String token = encode(jwtEncoder, claims("someone-else", now, now.plusSeconds(1800)));
        mockMvc.perform(post("/api/v1/transactions/init").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "attacker-controlled-signing-secret-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String token = encode(forger, claims(ISSUER, now, now.plusSeconds(1800)));
        mockMvc.perform(post("/api/v1/transactions/init").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientWithoutWriteScopeIsForbidden() throws Exception {
        String readOnly = token(READ_ONLY_CLIENT, SECRET_READ_ONLY);
        mockMvc.perform(post("/api/v1/transactions/init").header(HttpHeaders.AUTHORIZATION, bearer(readOnly)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        getStatus(readOnly, "TXN-0000000000000000").andExpect(status().isNotFound());
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/token']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transactions/init']").exists())
                .andExpect(jsonPath("$.components.schemas.TokenRequest.properties.client_id").exists())
                .andExpect(jsonPath("$.components.schemas.TokenRequest.properties.clientId").doesNotExist());
    }

    private static JwtClaimsSet claims(String issuer, Instant issuedAt, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(CLIENT_A)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", "transactions:write transactions:read")
                .build();
    }

    private static String encode(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
