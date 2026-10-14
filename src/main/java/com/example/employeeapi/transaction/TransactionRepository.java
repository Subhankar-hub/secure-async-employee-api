package com.example.employeeapi.transaction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<EmployeeTransaction, Long> {

    Optional<EmployeeTransaction> findByTransactionIdAndClientId(String transactionId, String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmployeeTransaction t where t.transactionId = :transactionId and t.clientId = :clientId")
    Optional<EmployeeTransaction> findForUpdate(String transactionId, String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmployeeTransaction t where t.id = :id")
    Optional<EmployeeTransaction> findByIdForUpdate(Long id);
}
