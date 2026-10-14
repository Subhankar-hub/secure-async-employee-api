package com.example.employeeapi.employee;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRequestValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static EmployeeRequest valid() {
        return new EmployeeRequest("Amit Kumar", LocalDate.of(1990, 5, 15), "MALE", "ABCDE1234F", "9876543210");
    }

    @Test
    void acceptsValidRequest() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void trimsNameAndNormalizesGender() {
        EmployeeRequest request = new EmployeeRequest("  Mary-Ann O'Neil Jr.  ", LocalDate.of(1985, 1, 1),
                "female", "ABCDE1234F", "6123456789");
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.name()).isEqualTo("Mary-Ann O'Neil Jr.");
        assertThat(request.normalizedGender()).isEqualTo(Gender.FEMALE);
    }

    static Stream<Arguments> invalidRequests() {
        LocalDate dob = LocalDate.of(1990, 5, 15);
        return Stream.of(
                Arguments.of(new EmployeeRequest(null, dob, "MALE", "ABCDE1234F", "9876543210"), "name"),
                Arguments.of(new EmployeeRequest("   ", dob, "MALE", "ABCDE1234F", "9876543210"), "name"),
                Arguments.of(new EmployeeRequest("A", dob, "MALE", "ABCDE1234F", "9876543210"), "name"),
                Arguments.of(new EmployeeRequest("A".repeat(101), dob, "MALE", "ABCDE1234F", "9876543210"), "name"),
                Arguments.of(new EmployeeRequest("Amit123", dob, "MALE", "ABCDE1234F", "9876543210"), "name"),
                Arguments.of(new EmployeeRequest("Amit <script>", dob, "MALE", "ABCDE1234F", "9876543210"), "name"),
                Arguments.of(new EmployeeRequest("Amit Kumar", null, "MALE", "ABCDE1234F", "9876543210"), "dob"),
                Arguments.of(new EmployeeRequest("Amit Kumar", LocalDate.now().plusDays(1), "MALE", "ABCDE1234F",
                        "9876543210"), "dob"),
                Arguments.of(new EmployeeRequest("Amit Kumar", LocalDate.now(), "MALE", "ABCDE1234F",
                        "9876543210"), "dob"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, null, "ABCDE1234F", "9876543210"), "gender"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "UNKNOWN", "ABCDE1234F", "9876543210"), "gender"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "M", "ABCDE1234F", "9876543210"), "gender"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", null, "9876543210"), "pan"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "abcde1234f", "9876543210"), "pan"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCD12345F", "9876543210"), "pan"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCDE1234", "9876543210"), "pan"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCDE1234F", null), "mobileNumber"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCDE1234F", "5876543210"),
                        "mobileNumber"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCDE1234F", "987654321"),
                        "mobileNumber"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCDE1234F", "98765432101"),
                        "mobileNumber"),
                Arguments.of(new EmployeeRequest("Amit Kumar", dob, "MALE", "ABCDE1234F", "98765abcde"),
                        "mobileNumber"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidField(EmployeeRequest request, String field) {
        Set<ConstraintViolation<EmployeeRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).allMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    @Test
    void toStringDoesNotExposePii() {
        assertThat(valid().toString()).doesNotContain("ABCDE1234F").doesNotContain("9876543210");
    }
}
