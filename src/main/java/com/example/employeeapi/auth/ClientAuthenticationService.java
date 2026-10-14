package com.example.employeeapi.auth;

import com.example.employeeapi.config.SecurityProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClientAuthenticationService {

    private final Map<String, SecurityProperties.Client> clients;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public ClientAuthenticationService(SecurityProperties properties) {
        DelegatingPasswordEncoder encoder =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        this.passwordEncoder = encoder;
        this.clients = properties.clients() == null ? Map.of() : properties.clients().stream()
                .filter(c -> c.clientId() != null && !c.clientId().isBlank())
                .filter(c -> c.secretHash() != null && !c.secretHash().isBlank())
                .collect(Collectors.toUnmodifiableMap(SecurityProperties.Client::clientId, Function.identity()));
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        this.dummyHash = encoder.encode(HexFormat.of().formatHex(random));
    }

    public Optional<AuthenticatedClient> authenticate(String clientId, String clientSecret) {
        SecurityProperties.Client client = clients.get(clientId);
        // Always run a BCrypt comparison so response timing does not reveal which client IDs exist.
        String hash = client != null ? client.secretHash() : dummyHash;
        boolean matches = passwordEncoder.matches(clientSecret, hash);
        if (client == null || !matches) {
            return Optional.empty();
        }
        List<String> scopes = client.scopes() == null ? List.of() : List.copyOf(client.scopes());
        return Optional.of(new AuthenticatedClient(client.clientId(), scopes));
    }

    public record AuthenticatedClient(String clientId, List<String> scopes) {
    }
}
