package com.example.employeeapi.employee;

import com.example.employeeapi.transaction.EmployeeTransaction;
import com.example.employeeapi.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeePersistenceService {

    private final TransactionRepository transactionRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeePersistenceService(TransactionRepository transactionRepository,
                                      EmployeeRepository employeeRepository) {
        this.transactionRepository = transactionRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Inserts the employee and marks the transaction COMPLETED in one database transaction,
     * so a failure leaves neither an employee row nor a COMPLETED status.
     */
    @Transactional
    public void persistAndComplete(Long transactionPk, EmployeeRequest request) {
        EmployeeTransaction transaction = transactionRepository.findByIdForUpdate(transactionPk)
                .orElseThrow(() -> new IllegalStateException("Transaction not found"));
        employeeRepository.saveAndFlush(new Employee(transaction, request.name(), request.dob(),
                request.normalizedGender(), request.pan(), request.mobileNumber()));
        transaction.markCompleted();
    }
}
