package com.example.employeeapi.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * State transitions performed by the background worker, each in its own database transaction.
 */
@Service
public class TransactionStateService {

    private static final Logger log = LoggerFactory.getLogger(TransactionStateService.class);

    private final TransactionRepository transactionRepository;

    public TransactionStateService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Moves an ACCEPTED transaction to PROCESSING. Returns false if it is not in ACCEPTED state,
     * so a duplicate or stale job does nothing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessing(Long id) {
        EmployeeTransaction transaction = transactionRepository.findByIdForUpdate(id).orElse(null);
        if (transaction == null || transaction.getStatus() != TransactionStatus.ACCEPTED) {
            log.warn("Skipping processing for transaction pk={}: not in ACCEPTED state", id);
            return false;
        }
        transaction.markProcessing();
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String code, String message) {
        transactionRepository.findByIdForUpdate(id).ifPresent(transaction -> {
            TransactionStatus status = transaction.getStatus();
            if (status == TransactionStatus.COMPLETED || status == TransactionStatus.FAILED) {
                return;
            }
            transaction.markFailed(code, message);
            log.info("Transaction {} failed with code {}", transaction.getTransactionId(), code);
        });
    }
}
