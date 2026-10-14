package com.example.employeeapi.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "employee_transaction")
public class EmployeeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false, length = 32)
    private String transactionId;

    @Column(name = "client_id", nullable = false, updatable = false, length = 100)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected EmployeeTransaction() {
    }

    public EmployeeTransaction(String transactionId, String clientId) {
        this.transactionId = transactionId;
        this.clientId = clientId;
        this.status = TransactionStatus.INITIALIZED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markAccepted() {
        requireStatus(TransactionStatus.INITIALIZED);
        status = TransactionStatus.ACCEPTED;
        submittedAt = Instant.now();
    }

    public void markProcessing() {
        requireStatus(TransactionStatus.ACCEPTED);
        status = TransactionStatus.PROCESSING;
    }

    public void markCompleted() {
        requireStatus(TransactionStatus.PROCESSING);
        status = TransactionStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String code, String message) {
        if (status == TransactionStatus.COMPLETED || status == TransactionStatus.FAILED) {
            throw new IllegalStateException("Cannot fail a transaction in status " + status);
        }
        status = TransactionStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        completedAt = Instant.now();
    }

    private void requireStatus(TransactionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getClientId() {
        return clientId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
