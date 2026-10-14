package com.example.employeeapi.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByPan(String pan);

    Optional<Employee> findByTransactionId(Long transactionPk);
}
