package com.example.employeeapi.auth;

import com.example.employeeapi.auth.ClientAuthenticationService.AuthenticatedClient;
import com.example.employeeapi.config.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties.Jwt jwtProperties;
    private final Clock clock;

    @Autowired
    public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    TokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = properties.jwt();
        this.clock = clock;
    }

    public TokenResponse issue(AuthenticatedClient client) {
        Instant now = clock.instant();
        long ttl = jwtProperties.ttlSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(client.clientId())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString())
                .claim("scope", String.join(" ", client.scopes()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl);
    }
}
