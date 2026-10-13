package com.pos.sales.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.common.web.PageResponse;
import com.pos.sales.dto.CheckoutRequest;
import com.pos.sales.dto.PreviewRequest;
import com.pos.sales.dto.PricePreviewResponse;
import com.pos.sales.dto.ReceiptResponse;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.dto.SaleSummaryResponse;
import com.pos.sales.entity.PaymentMethod;
import com.pos.sales.entity.SaleStatus;
import com.pos.sales.service.CheckoutService;
import com.pos.sales.service.PricePreviewService;
import com.pos.sales.service.SaleQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@Tag(name = "Sales")
public class SalesController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAY_HEADER = "Idempotent-Replay";

    private final CheckoutService checkoutService;
    private final PricePreviewService pricePreviewService;
    private final SaleQueryService saleQueryService;

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Complete a sale (CASHIER+)",
            description = "Atomically prices the basket on the server, records the cashier-confirmed payment, "
                    + "deducts stock and appends ledger entries. Requires an Idempotency-Key header: retrying "
                    + "the same key with the same body returns the original sale (200, Idempotent-Replay: true); "
                    + "the same key with a different body is rejected (409). Client-side totals are never used.")
    public ResponseEntity<SaleResponse> checkout(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Parameter(description = "Unique per checkout attempt, 1-64 chars [A-Za-z0-9_-]", required = true)
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        CheckoutService.Outcome outcome = checkoutService.checkout(actor, idempotencyKey, request);
        if (outcome.replayed()) {
            return ResponseEntity.ok().header(REPLAY_HEADER, "true").body(outcome.sale());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, "/api/sales/" + outcome.sale().id())
                .body(outcome.sale());
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Price a basket without creating a sale",
            description = "Returns the same server-calculated subtotal, discount, tax and total that checkout would use.")
    public PricePreviewResponse preview(
            @AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody PreviewRequest request) {
        return pricePreviewService.preview(actor, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "All sales, newest first (MANAGER+)")
    public PageResponse<SaleSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String saleNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var filter = new SaleQueryService.Filter(saleNumber, from, to, cashierId, paymentMethod, status);
        return PageResponse.of(saleQueryService.list(actor, filter, false, pageable(page, size)), s -> s);
    }

    @GetMapping("/my-sales")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "The caller's own sales, newest first")
    public PageResponse<SaleSummaryResponse> mySales(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String saleNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var filter = new SaleQueryService.Filter(saleNumber, from, to, null, paymentMethod, null);
        return PageResponse.of(saleQueryService.list(actor, filter, true, pageable(page, size)), s -> s);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Sale details (own sales for cashiers; any sale for MANAGER+)")
    public SaleResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return saleQueryService.get(actor, id);
    }

    @GetMapping("/receipt/{saleNumber}")
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Receipt data for a sale number; reprinting never modifies the sale")
    public ReceiptResponse receipt(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String saleNumber) {
        return saleQueryService.receipt(actor, saleNumber);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "soldAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
