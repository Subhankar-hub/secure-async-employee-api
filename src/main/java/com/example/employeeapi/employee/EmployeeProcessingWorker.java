package com.example.employeeapi.employee;

import com.example.employeeapi.config.AsyncConfig;
import com.example.employeeapi.transaction.TransactionStateService;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmployeeProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProcessingWorker.class);
    static final String PAN_CONSTRAINT = "uk_employee_pan";

    private final TransactionStateService stateService;
    private final EmployeePersistenceService persistenceService;

    public EmployeeProcessingWorker(TransactionStateService stateService,
                                    EmployeePersistenceService persistenceService) {
        this.stateService = stateService;
        this.persistenceService = persistenceService;
    }

    @Async(AsyncConfig.EMPLOYEE_EXECUTOR)
    public void process(EmployeeSubmittedEvent event) {
        Long pk = event.transactionPk();
        try {
            if (!stateService.markProcessing(pk)) {
                return;
            }
            persistenceService.persistAndComplete(pk, event.employee());
            log.info("Transaction {} completed", event.transactionId());
        } catch (DataIntegrityViolationException e) {
            // Exception messages may contain PII (e.g. the PAN value), so only the type is logged.
            log.warn("Transaction {} failed: integrity violation ({})", event.transactionId(),
                    e.getClass().getSimpleName());
            if (isPanViolation(e)) {
                fail(event, "DUPLICATE_PAN", "Employee PAN already exists");
            } else {
                fail(event, "PERSISTENCE_ERROR", "Employee data could not be saved");
            }
        } catch (RuntimeException e) {
            log.error("Transaction {} failed: unexpected {}", event.transactionId(), e.getClass().getSimpleName());
            fail(event, "PROCESSING_ERROR", "Employee data could not be processed");
        }
    }

    private void fail(EmployeeSubmittedEvent event, String code, String message) {
        try {
            stateService.markFailed(event.transactionPk(), code, message);
        } catch (RuntimeException e) {
            log.error("Could not record failure for transaction {} ({})", event.transactionId(),
                    e.getClass().getSimpleName());
        }
    }

    static boolean isPanViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && PAN_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
