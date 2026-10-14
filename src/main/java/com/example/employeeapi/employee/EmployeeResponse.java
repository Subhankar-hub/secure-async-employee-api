package com.example.employeeapi.employee;

import com.example.employeeapi.transaction.TransactionStatus;

import java.time.LocalDate;

public record EmployeeResponse(String transactionId, TransactionStatus status, EmployeeView employee) {

    public record EmployeeView(String name, LocalDate dob, Gender gender, String pan, String mobileNumber) {

        static EmployeeView from(Employee employee) {
            return new EmployeeView(employee.getName(), employee.getDob(), employee.getGender(),
                    employee.getPan(), employee.getMobileNumber());
        }
    }
}
