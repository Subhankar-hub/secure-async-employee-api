package com.example.employeeapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionFlowIT extends AbstractIntegrationTest {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void tokens() throws Exception {
        tokenA = token(CLIENT_A, SECRET_A);
        tokenB = token(CLIENT_B, SECRET_B);
    }

    @Test
    void initCreatesUniqueTransactionsOwnedByAuthenticatedClient() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/init")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType("application/json")
                        .content("{\"client_id\":\"secondary_client\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("INITIALIZED"))
                .andExpect(jsonPath("$.message").value("Transaction initialized successfully"))
                .andExpect(jsonPath("$.transaction_id").value(org.hamcrest.Matchers.matchesPattern("TXN-[0-9A-F]{16}")));

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            ids.add(initTransaction(tokenA));
        }
        assertThat(ids).hasSize(20);
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT client_id FROM employee_transaction", String.class))
                .containsExactly(CLIENT_A);
        getStatus(tokenA, ids.iterator().next())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INITIALIZED"));
    }

    @Test
    void validSubmissionIsAcceptedProcessedAsynchronouslyAndReadBackFromDatabase() throws Exception {
        String txn = initTransaction(tokenA);
        getEmployee(tokenA, txn)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_READY"));

        submit(tokenA, txn, employee("ABCDE1234F"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transaction_id").value(txn))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value("Employee data accepted for processing"));

        JsonNode finalStatus = awaitTerminalStatus(tokenA, txn);
        assertThat(finalStatus.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finalStatus.get("message").asText()).isEqualTo("Employee data saved successfully");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT e.name, e.dob, e.gender, e.pan, e.mobile_number, t.completed_at, t.submitted_at "
                        + "FROM employee e JOIN employee_transaction t ON t.id = e.transaction_pk "
                        + "WHERE t.transaction_id = ?", txn);
        assertThat(row.get("completed_at")).isNotNull();
        assertThat(row.get("submitted_at")).isNotNull();

        getEmployee(tokenA, txn)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction_id").value(txn))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.employee.name").value(row.get("name")))
                .andExpect(jsonPath("$.employee.dob").value(row.get("dob").toString()))
                .andExpect(jsonPath("$.employee.gender").value(row.get("gender")))
                .andExpect(jsonPath("$.employee.pan").value(row.get("pan")))
                .andExpect(jsonPath("$.employee.mobile_number").value(row.get("mobile_number")));
        assertThat(row.get("pan")).isEqualTo("ABCDE1234F");
        assertThat(row.get("mobile_number")).isEqualTo("9876543210");
    }

    @Test
    void nameIsTrimmedAndGenderNormalizedBeforeStorage() throws Exception {
        String txn = initTransaction(tokenA);
        Map<String, Object> body = new HashMap<>(employee("PQRST6789Z"));
        body.put("name", "  Priya O'Brien-Shah  ");
        body.put("gender", "female");
        submit(tokenA, txn, body).andExpect(status().isAccepted());

        assertThat(awaitTerminalStatus(tokenA, txn).get("status").asText()).isEqualTo("COMPLETED");
        getEmployee(tokenA, txn)
                .andExpect(jsonPath("$.employee.name").value("Priya O'Brien-Shah"))
                .andExpect(jsonPath("$.employee.gender").value("FEMALE"));
    }

    @Test
    void repeatedSubmissionToSameTransactionIsRejectedWithoutDuplicateProcessing() throws Exception {
        String txn = initTransaction(tokenA);
        submit(tokenA, txn, employee("ABCDE1234F")).andExpect(status().isAccepted());
        submit(tokenA, txn, employee("ZZZZZ9999Z"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_ALREADY_SUBMITTED"));

        assertThat(awaitTerminalStatus(tokenA, txn).get("status").asText()).isEqualTo("COMPLETED");
        submit(tokenA, txn, employee("YYYYY8888Y")).andExpect(status().isConflict());
        assertThat(employeeCount()).isEqualTo(1);
    }

    @Test
    void duplicatePanIsRejectedAtRequestStage() throws Exception {
        String first = initTransaction(tokenA);
        submit(tokenA, first, employee("ABCDE1234F")).andExpect(status().isAccepted());
        awaitTerminalStatus(tokenA, first);

        String second = initTransaction(tokenB);
        submit(tokenB, second, employee("ABCDE1234F"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PAN"))
                .andExpect(jsonPath("$.message").value("Employee PAN already exists"));

        assertThat(dbStatus(second)).isEqualTo("INITIALIZED");
        assertThat(employeeCount()).isEqualTo(1);
    }

    @Test
    void anotherClientsTransactionIsConcealedAsNotFound() throws Exception {
        String txn = initTransaction(tokenA);
        getStatus(tokenB, txn).andExpect(status().isNotFound());
        getEmployee(tokenB, txn).andExpect(status().isNotFound());
        submit(tokenB, txn, employee("ABCDE1234F"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
        assertThat(dbStatus(txn)).isEqualTo("INITIALIZED");

        submit(tokenA, txn, employee("ABCDE1234F")).andExpect(status().isAccepted());
        awaitTerminalStatus(tokenA, txn);
        getEmployee(tokenB, txn).andExpect(status().isNotFound());
    }

    @Test
    void unknownTransactionCannotBeUsed() throws Exception {
        String unknown = "TXN-DEADBEEFDEADBEEF";
        getStatus(tokenA, unknown).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        getEmployee(tokenA, unknown).andExpect(status().isNotFound());
        submit(tokenA, unknown, employee("ABCDE1234F")).andExpect(status().isNotFound());
        assertThat(employeeCount()).isZero();
    }

    static Stream<Arguments> invalidPayloads() {
        String future = LocalDate.now().plusDays(1).toString();
        return Stream.of(
                Arguments.of(with("name", "A"), "name"),
                Arguments.of(with("name", "   "), "name"),
                Arguments.of(with("name", "Amit 123"), "name"),
                Arguments.of(without("name"), "name"),
                Arguments.of(with("dob", "15-05-1990"), "dob"),
                Arguments.of(with("dob", "1990-02-30"), "dob"),
                Arguments.of(with("dob", future), "dob"),
                Arguments.of(without("dob"), "dob"),
                Arguments.of(with("gender", "UNKNOWN"), "gender"),
                Arguments.of(without("gender"), "gender"),
                Arguments.of(with("pan", "abcde1234f"), "pan"),
                Arguments.of(with("pan", "ABCDE12345"), "pan"),
                Arguments.of(without("pan"), "pan"),
                Arguments.of(with("mobile_number", "5876543210"), "mobile_number"),
                Arguments.of(with("mobile_number", "98765"), "mobile_number"),
                Arguments.of(with("mobile_number", 9876543210L), "mobile_number"),
                Arguments.of(without("mobile_number"), "mobile_number"));
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void invalidEmployeeDataIsRejectedWithFieldDetails(Map<String, Object> body, String field) throws Exception {
        String txn = initTransaction(tokenA);
        submit(tokenA, txn, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details." + field).isNotEmpty());
        assertThat(dbStatus(txn)).isEqualTo("INITIALIZED");
        assertThat(employeeCount()).isZero();
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        String txn = initTransaction(tokenA);
        submit(tokenA, txn, "{\"name\": \"Amit\",")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
    }

    private static Map<String, Object> with(String field, Object value) {
        Map<String, Object> body = new HashMap<>(employee("ABCDE1234F"));
        body.put(field, value);
        return body;
    }

    private static Map<String, Object> without(String field) {
        Map<String, Object> body = new HashMap<>(employee("ABCDE1234F"));
        body.remove(field);
        return body;
    }
}
