package com.example.employeeapi.employee;

import com.example.employeeapi.error.ApiException;
import com.example.employeeapi.transaction.EmployeeTransaction;
import com.example.employeeapi.transaction.TransactionRepository;
import com.example.employeeapi.transaction.TransactionResponse;
import com.example.employeeapi.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeSubmissionServiceTest {

    private static final String CLIENT = "assessment_client";
    private static final String TXN = "TXN-0011223344556677";

    @Mock
    TransactionRepository transactionRepository;
    @Mock
    EmployeeRepository employeeRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @InjectMocks
    EmployeeSubmissionService service;

    private final EmployeeRequest request =
            new EmployeeRequest("Amit Kumar", LocalDate.of(1990, 5, 15), "MALE", "ABCDE1234F", "9876543210");

    @Test
    void acceptsInitializedTransactionAndPublishesEvent() {
        EmployeeTransaction transaction = new EmployeeTransaction(TXN, CLIENT);
        when(transactionRepository.findForUpdate(TXN, CLIENT)).thenReturn(Optional.of(transaction));
        when(employeeRepository.existsByPan("ABCDE1234F")).thenReturn(false);

        TransactionResponse response = service.submit(CLIENT, TXN, request);

        assertThat(response.status()).isEqualTo(TransactionStatus.ACCEPTED);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.ACCEPTED);
        assertThat(transaction.getSubmittedAt()).isNotNull();
        ArgumentCaptor<EmployeeSubmittedEvent> event = ArgumentCaptor.forClass(EmployeeSubmittedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().transactionId()).isEqualTo(TXN);
        assertThat(event.getValue().employee()).isEqualTo(request);
    }

    @Test
    void unknownOrForeignTransactionIsNotFound() {
        when(transactionRepository.findForUpdate(TXN, CLIENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(CLIENT, TXN, request))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void alreadySubmittedTransactionConflicts() {
        EmployeeTransaction transaction = new EmployeeTransaction(TXN, CLIENT);
        transaction.markAccepted();
        when(transactionRepository.findForUpdate(TXN, CLIENT)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.submit(CLIENT, TXN, request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("TRANSACTION_ALREADY_SUBMITTED");
                });
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void existingPanConflictsAndLeavesTransactionInitialized() {
        EmployeeTransaction transaction = new EmployeeTransaction(TXN, CLIENT);
        when(transactionRepository.findForUpdate(TXN, CLIENT)).thenReturn(Optional.of(transaction));
        when(employeeRepository.existsByPan("ABCDE1234F")).thenReturn(true);

        assertThatThrownBy(() -> service.submit(CLIENT, TXN, request))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DUPLICATE_PAN"));
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.INITIALIZED);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
