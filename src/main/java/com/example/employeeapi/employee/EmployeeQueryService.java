package com.example.employeeapi.employee;

import com.example.employeeapi.error.ApiException;
import com.example.employeeapi.transaction.EmployeeTransaction;
import com.example.employeeapi.transaction.TransactionService;
import com.example.employeeapi.transaction.TransactionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeQueryService {

    private final TransactionService transactionService;
    private final EmployeeRepository employeeRepository;

    public EmployeeQueryService(TransactionService transactionService, EmployeeRepository employeeRepository) {
        this.transactionService = transactionService;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getSavedEmployee(String clientId, String transactionId) {
        EmployeeTransaction transaction = transactionService.getOwned(clientId, transactionId);
        if (transaction.getStatus() == TransactionStatus.FAILED) {
            throw ApiException.conflict("TRANSACTION_FAILED", "Transaction failed; no employee record was saved");
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw ApiException.conflict("EMPLOYEE_NOT_READY", "Employee data is not yet available");
        }
        Employee employee = employeeRepository.findByTransactionId(transaction.getId())
                .orElseThrow(() -> new IllegalStateException("Completed transaction has no employee record"));
        return new EmployeeResponse(transaction.getTransactionId(), transaction.getStatus(),
                EmployeeResponse.EmployeeView.from(employee));
    }
}
