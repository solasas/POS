package com.pos.settings.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.settings.dto.SettingsResponse;
import com.pos.settings.dto.UpdateSettingsRequest;
import com.pos.settings.service.AppSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings")
public class SettingsController {

    private final AppSettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasRole('CASHIER')")
    @Operation(summary = "Current store settings")
    public SettingsResponse get() {
        return settingsService.current();
    }

    @PutMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Change store settings (MANAGER+)",
            description = "allowNegativeStock=true lets checkout continue after a product's stock reaches zero; "
                    + "stock then goes negative and is recorded in the ledger. Default is false.")
    public SettingsResponse update(
            @AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody UpdateSettingsRequest request) {
        return settingsService.update(actor, request.allowNegativeStock());
    }
}
