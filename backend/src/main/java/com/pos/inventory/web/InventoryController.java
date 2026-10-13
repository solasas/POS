package com.pos.inventory.web;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.common.web.PageResponse;
import com.pos.inventory.dto.AdjustmentRequest;
import com.pos.inventory.dto.InventoryTransactionResponse;
import com.pos.inventory.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Adjust stock (MANAGER+)",
            description = "Signed quantityChange: positive receives/adds stock, negative removes it. "
                    + "A reason is required. Appends an ADJUSTMENT entry to the immutable ledger.")
    public InventoryTransactionResponse adjust(
            @AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody AdjustmentRequest request) {
        return inventoryService.adjust(actor, request);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Inventory ledger, newest first (MANAGER+)")
    public PageResponse<InventoryTransactionResponse> history(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID saleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(inventoryService.history(productId, saleId, pageable), t -> t);
    }
}
