package com.example.employeeapi.transaction;

import com.example.employeeapi.employee.EmployeeQueryService;
import com.example.employeeapi.employee.EmployeeRequest;
import com.example.employeeapi.employee.EmployeeResponse;
import com.example.employeeapi.employee.EmployeeSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final EmployeeSubmissionService submissionService;
    private final EmployeeQueryService employeeQueryService;

    public TransactionController(TransactionService transactionService,
                                 EmployeeSubmissionService submissionService,
                                 EmployeeQueryService employeeQueryService) {
        this.transactionService = transactionService;
        this.submissionService = submissionService;
        this.employeeQueryService = employeeQueryService;
    }

    @PostMapping("/init")
    public ResponseEntity<TransactionResponse> initialize(@AuthenticationPrincipal Jwt jwt) {
        EmployeeTransaction transaction = transactionService.initialize(jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + transaction.getTransactionId()))
                .body(TransactionResponse.of(transaction.getTransactionId(), transaction.getStatus(),
                        "Transaction initialized successfully"));
    }

    @PostMapping("/{transactionId}/employees")
    public ResponseEntity<TransactionResponse> submitEmployee(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable String transactionId,
                                                              @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.accepted().body(submissionService.submit(jwt.getSubject(), transactionId, request));
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse getStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String transactionId) {
        return TransactionResponse.statusOf(transactionService.getOwned(jwt.getSubject(), transactionId));
    }

    @GetMapping("/{transactionId}/employees")
    public EmployeeResponse getEmployee(@AuthenticationPrincipal Jwt jwt, @PathVariable String transactionId) {
        return employeeQueryService.getSavedEmployee(jwt.getSubject(), transactionId);
    }
}
