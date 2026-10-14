package com.example.employeeapi.employee;

import com.example.employeeapi.transaction.EmployeeTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_pk", nullable = false, unique = true, updatable = false)
    private EmployeeTransaction transaction;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDate dob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false, unique = true, length = 10)
    private String pan;

    @Column(name = "mobile_number", nullable = false, length = 10)
    private String mobileNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Employee() {
    }

    public Employee(EmployeeTransaction transaction, String name, LocalDate dob, Gender gender,
                    String pan, String mobileNumber) {
        this.transaction = transaction;
        this.name = name;
        this.dob = dob;
        this.gender = gender;
        this.pan = pan;
        this.mobileNumber = mobileNumber;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public EmployeeTransaction getTransaction() {
        return transaction;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDob() {
        return dob;
    }

    public Gender getGender() {
        return gender;
    }

    public String getPan() {
        return pan;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
