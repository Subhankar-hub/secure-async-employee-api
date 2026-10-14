package com.example.employeeapi;

import com.example.employeeapi.employee.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessingFailureIT extends AbstractIntegrationTest {

    @MockitoSpyBean
    EmployeeRepository employeeRepository;

    private String tokenA;

    @BeforeEach
    void token() throws Exception {
        tokenA = token(CLIENT_A, SECRET_A);
    }

    @Test
    void persistenceFailureAfterInsertMarksFailedAndLeavesNoEmployeeRow() throws Exception {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("simulated failure: jdbc:postgresql://db/secret ABCDE1234F");
        }).when(employeeRepository).saveAndFlush(any());

        String txn = initTransaction(tokenA);
        submit(tokenA, txn, employee("ABCDE1234F")).andExpect(status().isAccepted());

        JsonNode result = awaitTerminalStatus(tokenA, txn);
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("error").get("code").asText()).isEqualTo("PROCESSING_ERROR");
        assertThat(result.toString()).doesNotContain("simulated").doesNotContain("jdbc").doesNotContain("ABCDE1234F");
        assertThat(employeeCount()).isZero();

        getEmployee(tokenA, txn)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_FAILED"));
        submit(tokenA, txn, employee("ABCDE1234F")).andExpect(status().isConflict());
    }

    @Test
    void duplicatePanCaughtByDatabaseConstraintMarksFailedWithSafeError() throws Exception {
        String first = initTransaction(tokenA);
        submit(tokenA, first, employee("ABCDE1234F")).andExpect(status().isAccepted());
        assertThat(awaitTerminalStatus(tokenA, first).get("status").asText()).isEqualTo("COMPLETED");

        // Simulate the race where the request-stage PAN check ran before the other record committed.
        doReturn(false).when(employeeRepository).existsByPan(anyString());
        String second = initTransaction(tokenA);
        submit(tokenA, second, employee("ABCDE1234F")).andExpect(status().isAccepted());

        JsonNode result = awaitTerminalStatus(tokenA, second);
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("error").get("code").asText()).isEqualTo("DUPLICATE_PAN");
        assertThat(result.get("error").get("message").asText()).isEqualTo("Employee PAN already exists");
        assertThat(result.toString()).doesNotContain("uk_employee_pan").doesNotContain("SQL");
        assertThat(employeeCount()).isEqualTo(1);
    }
}
