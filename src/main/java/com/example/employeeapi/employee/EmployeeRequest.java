package com.example.employeeapi.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Locale;

public record EmployeeRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$",
                message = "Name may contain only letters, spaces, periods, apostrophes and hyphens")
        String name,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dob,

        @NotNull(message = "Gender is required")
        @Pattern(regexp = "MALE|FEMALE|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Gender must be one of MALE, FEMALE, OTHER")
        String gender,

        @NotNull(message = "PAN is required")
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$",
                message = "PAN must be 5 uppercase letters, 4 digits and 1 uppercase letter")
        String pan,

        @NotNull(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$",
                message = "Mobile number must contain 10 digits and start with 6, 7, 8, or 9")
        String mobileNumber) {

    public EmployeeRequest {
        name = name == null ? null : name.strip();
    }

    public Gender normalizedGender() {
        return Gender.valueOf(gender.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "EmployeeRequest[pan=" + PiiMasker.maskPan(pan) + "]";
    }
}
