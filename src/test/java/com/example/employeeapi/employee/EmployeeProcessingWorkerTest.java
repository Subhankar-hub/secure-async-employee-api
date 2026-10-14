package com.example.employeeapi.employee;

import com.example.employeeapi.transaction.TransactionStateService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeProcessingWorkerTest {

    @Mock
    TransactionStateService stateService;
    @Mock
    EmployeePersistenceService persistenceService;
    @InjectMocks
    EmployeeProcessingWorker worker;

    private final EmployeeRequest request =
            new EmployeeRequest("Amit Kumar", LocalDate.of(1990, 5, 15), "MALE", "ABCDE1234F", "9876543210");
    private final EmployeeSubmittedEvent event = new EmployeeSubmittedEvent(1L, "TXN-1", request);

    @Test
    void movesToProcessingThenPersistsAndCompletes() {
        when(stateService.markProcessing(1L)).thenReturn(true);

        worker.process(event);

        InOrder order = inOrder(stateService, persistenceService);
        order.verify(stateService).markProcessing(1L);
        order.verify(persistenceService).persistAndComplete(1L, request);
        verify(stateService, never()).markFailed(anyLong(), anyString(), anyString());
    }

    @Test
    void skipsWhenTransactionIsNoLongerAccepted() {
        when(stateService.markProcessing(1L)).thenReturn(false);

        worker.process(event);

        verify(persistenceService, never()).persistAndComplete(any(), any());
    }

    @Test
    void panConstraintViolationIsRecordedAsDuplicatePan() {
        when(stateService.markProcessing(1L)).thenReturn(true);
        DataIntegrityViolationException e = new DataIntegrityViolationException("dup",
                new ConstraintViolationException("dup", new SQLException("dup"), "uk_employee_pan"));
        doThrow(e).when(persistenceService).persistAndComplete(1L, request);

        worker.process(event);

        verify(stateService).markFailed(1L, "DUPLICATE_PAN", "Employee PAN already exists");
    }

    @Test
    void otherIntegrityViolationIsRecordedAsPersistenceError() {
        when(stateService.markProcessing(1L)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("other")).when(persistenceService)
                .persistAndComplete(1L, request);

        worker.process(event);

        verify(stateService).markFailed(eq(1L), eq("PERSISTENCE_ERROR"), anyString());
    }

    @Test
    void unexpectedFailureIsRecordedAsProcessingError() {
        when(stateService.markProcessing(1L)).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(persistenceService).persistAndComplete(1L, request);

        worker.process(event);

        verify(stateService).markFailed(eq(1L), eq("PROCESSING_ERROR"), anyString());
    }
}
