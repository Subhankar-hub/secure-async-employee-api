package com.example.employeeapi.transaction;

import com.example.employeeapi.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public EmployeeTransaction initialize(String clientId) {
        EmployeeTransaction transaction = transactionRepository.save(
                new EmployeeTransaction(generateTransactionId(), clientId));
        log.info("Initialized transaction {}", transaction.getTransactionId());
        return transaction;
    }

    @Transactional(readOnly = true)
    public EmployeeTransaction getOwned(String clientId, String transactionId) {
        return transactionRepository.findByTransactionIdAndClientId(transactionId, clientId)
                .orElseThrow(ApiException::notFound);
    }

    static String generateTransactionId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "TXN-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
