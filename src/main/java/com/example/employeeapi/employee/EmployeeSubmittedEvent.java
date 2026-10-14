package com.example.employeeapi.employee;

public record EmployeeSubmittedEvent(Long transactionPk, String transactionId, EmployeeRequest employee) {
}
