package com.example.employeeapi.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(String transactionId, TransactionStatus status, String message,
                                  ErrorInfo error) {

    public record ErrorInfo(String code, String message) {
    }

    public static TransactionResponse of(String transactionId, TransactionStatus status, String message) {
        return new TransactionResponse(transactionId, status, message, null);
    }

    public static TransactionResponse statusOf(EmployeeTransaction transaction) {
        return switch (transaction.getStatus()) {
            case COMPLETED -> of(transaction.getTransactionId(), TransactionStatus.COMPLETED,
                    "Employee data saved successfully");
            case FAILED -> new TransactionResponse(transaction.getTransactionId(), TransactionStatus.FAILED, null,
                    new ErrorInfo(transaction.getErrorCode(), transaction.getErrorMessage()));
            default -> of(transaction.getTransactionId(), transaction.getStatus(), null);
        };
    }
}
