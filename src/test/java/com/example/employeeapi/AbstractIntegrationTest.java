package com.example.employeeapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against a real PostgreSQL database. Requires DB_USERNAME and DB_PASSWORD (and optionally
 * DB_HOST, DB_PORT, DB_NAME, TEST_DB_SCHEMA) in the environment; the test schema is truncated before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String CLIENT_A = "assessment_client";
    protected static final String CLIENT_B = "secondary_client";
    protected static final String READ_ONLY_CLIENT = "readonly_client";
    protected static final String SECRET_A = "secret-a-for-integration-tests";
    protected static final String SECRET_B = "secret-b-for-integration-tests";
    protected static final String SECRET_READ_ONLY = "secret-ro-for-integration-tests";
    protected static final String JWT_SECRET = "integration-test-jwt-signing-secret-0123456789";
    protected static final String ISSUER = "secure-async-employee-api-it";

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        registry.add("DB_SCHEMA", () -> Optional.ofNullable(System.getenv("TEST_DB_SCHEMA")).orElse("app_schema_test"));
        registry.add("app.security.jwt.secret", () -> JWT_SECRET);
        registry.add("app.security.jwt.issuer", () -> ISSUER);
        registry.add("app.security.clients[0].client-id", () -> CLIENT_A);
        registry.add("app.security.clients[0].secret-hash", () -> bcrypt.encode(SECRET_A));
        registry.add("app.security.clients[0].scopes", () -> "transactions:write,transactions:read");
        registry.add("app.security.clients[1].client-id", () -> CLIENT_B);
        registry.add("app.security.clients[1].secret-hash", () -> bcrypt.encode(SECRET_B));
        registry.add("app.security.clients[1].scopes", () -> "transactions:write,transactions:read");
        registry.add("app.security.clients[2].client-id", () -> READ_ONLY_CLIENT);
        registry.add("app.security.clients[2].secret-hash", () -> bcrypt.encode(SECRET_READ_ONLY));
        registry.add("app.security.clients[2].scopes", () -> "transactions:read");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE employee, employee_transaction RESTART IDENTITY CASCADE");
    }

    protected String token(String clientId, String secret) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("client_id", clientId, "client_secret", secret))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("access_token").asText();
    }

    protected String initTransaction(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/transactions/init").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("transaction_id").asText();
    }

    protected ResultActions submit(String token, String transactionId, Object body) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/{id}/employees", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json(body)));
    }

    protected ResultActions getStatus(String token, String transactionId) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    protected ResultActions getEmployee(String token, String transactionId) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions/{id}/employees", transactionId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    protected JsonNode awaitTerminalStatus(String token, String transactionId) {
        JsonNode[] last = new JsonNode[1];
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> {
            last[0] = read(getStatus(token, transactionId).andExpect(status().isOk()).andReturn());
            return TERMINAL.contains(last[0].get("status").asText());
        });
        return last[0];
    }

    protected static Map<String, Object> employee(String pan) {
        return Map.of(
                "name", "Amit Kumar",
                "dob", "1990-05-15",
                "gender", "MALE",
                "pan", pan,
                "mobile_number", "9876543210");
    }

    protected int employeeCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM employee", Integer.class);
    }

    protected String dbStatus(String transactionId) {
        return jdbcTemplate.queryForObject("SELECT status FROM employee_transaction WHERE transaction_id = ?",
                String.class, transactionId);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
