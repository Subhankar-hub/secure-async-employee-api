package com.example.employeeapi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIT extends AbstractIntegrationTest {

    private static final int THREADS = 10;

    @Test
    void concurrentSubmissionsToOneTransactionAcceptExactlyOne() throws Exception {
        String token = token(CLIENT_A, SECRET_A);
        String txn = initTransaction(token);

        List<Integer> statuses = runConcurrently(i -> submit(token, txn, employee(pan(i)))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 202).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(THREADS - 1);
        assertThat(awaitTerminalStatus(token, txn).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(employeeCount()).isEqualTo(1);
    }

    @Test
    void concurrentSubmissionsWithSamePanCreateExactlyOneEmployee() throws Exception {
        String token = token(CLIENT_A, SECRET_A);
        List<String> transactions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            transactions.add(initTransaction(token));
        }

        List<Integer> statuses = runConcurrently(i -> submit(token, transactions.get(i), employee("SAMEP1234N"))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).allMatch(s -> s == 202 || s == 409);

        List<String> finalStates = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String txn = transactions.get(i);
            if (statuses.get(i) == 202) {
                var result = awaitTerminalStatus(token, txn);
                finalStates.add(result.get("status").asText());
                if ("FAILED".equals(result.get("status").asText())) {
                    assertThat(result.get("error").get("code").asText()).isEqualTo("DUPLICATE_PAN");
                }
            } else {
                assertThat(dbStatus(txn)).isEqualTo("INITIALIZED");
            }
        }

        assertThat(finalStates).filteredOn("COMPLETED"::equals).hasSize(1);
        assertThat(employeeCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employee_transaction WHERE status IN ('ACCEPTED', 'PROCESSING')", Integer.class))
                .isZero();
    }

    private static String pan(int i) {
        return "ABCDE" + String.format("%04d", i) + "F";
    }

    private interface IndexedCall {
        Integer call(int index) throws Exception;
    }

    private static List<Integer> runConcurrently(IndexedCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return call.call(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
