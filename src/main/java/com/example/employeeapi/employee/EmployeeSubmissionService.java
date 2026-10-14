package com.example.employeeapi.employee;

import com.example.employeeapi.error.ApiException;
import com.example.employeeapi.transaction.EmployeeTransaction;
import com.example.employeeapi.transaction.TransactionRepository;
import com.example.employeeapi.transaction.TransactionResponse;
import com.example.employeeapi.transaction.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSubmissionService.class);

    private final TransactionRepository transactionRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmployeeSubmissionService(TransactionRepository transactionRepository,
                                     EmployeeRepository employeeRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.employeeRepository = employeeRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Accepts a submission and schedules background processing. The row lock serialises concurrent
     * submissions to the same transaction; the processing job is only dispatched after this commit.
     */
    @Transactional
    public TransactionResponse submit(String clientId, String transactionId, EmployeeRequest request) {
        EmployeeTransaction transaction = transactionRepository.findForUpdate(transactionId, clientId)
                .orElseThrow(ApiException::notFound);
        if (transaction.getStatus() != TransactionStatus.INITIALIZED) {
            throw ApiException.conflict("TRANSACTION_ALREADY_SUBMITTED",
                    "Employee data has already been submitted for this transaction");
        }
        if (employeeRepository.existsByPan(request.pan())) {
            throw ApiException.conflict("DUPLICATE_PAN", "Employee PAN already exists");
        }
        transaction.markAccepted();
        eventPublisher.publishEvent(new EmployeeSubmittedEvent(transaction.getId(), transactionId, request));
        log.info("Transaction {} accepted for processing (pan={})", transactionId, PiiMasker.maskPan(request.pan()));
        return TransactionResponse.of(transactionId, TransactionStatus.ACCEPTED, "Employee data accepted for processing");
    }
}
