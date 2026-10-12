package com.pos.sales.web;

import java.util.UUID;

import org.springframework.security.access.method.P;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.sales.dto.DiscountRequest;
import com.pos.sales.dto.TransactionResponse;
import com.pos.sales.service.TransactionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('MANAGER')")
    public TransactionResponse voidTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return TransactionResponse.from(transactionService.voidTransaction(id, actor));
    }

    @PostMapping("/{id}/discount")
    @PreAuthorize("hasRole('CASHIER') and (#request.percentage() <= 15 or hasRole('MANAGER'))")
    public TransactionResponse applyDiscount(
            @PathVariable UUID id,
            @P("request") @Valid @RequestBody DiscountRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return TransactionResponse.from(transactionService.applyDiscount(id, request, actor));
    }
}
