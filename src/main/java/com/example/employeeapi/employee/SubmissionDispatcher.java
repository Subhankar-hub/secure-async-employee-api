package com.example.employeeapi.employee;

import com.example.employeeapi.transaction.TransactionStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands accepted submissions to the background worker only after the acceptance has committed,
 * so the worker never sees an uncommitted ACCEPTED state.
 */
@Component
public class SubmissionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SubmissionDispatcher.class);

    private final EmployeeProcessingWorker worker;
    private final TransactionStateService stateService;

    public SubmissionDispatcher(EmployeeProcessingWorker worker, TransactionStateService stateService) {
        this.worker = worker;
        this.stateService = stateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeSubmitted(EmployeeSubmittedEvent event) {
        try {
            worker.process(event);
        } catch (TaskRejectedException e) {
            log.error("Executor rejected processing for transaction {}", event.transactionId());
            stateService.markFailed(event.transactionPk(), "PROCESSING_UNAVAILABLE",
                    "Processing capacity exceeded; please retry with a new transaction");
        }
    }
}
