package com.pos.settings.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.security.AuthenticatedUser;
import com.pos.common.entity.AuditLog;
import com.pos.common.repository.AuditLogRepository;
import com.pos.settings.dto.SettingsResponse;
import com.pos.settings.entity.AppSetting;
import com.pos.settings.repository.AppSettingRepository;

import lombok.RequiredArgsConstructor;

/** Store-level switches. Changes are audited and logged; only MANAGER+ can reach {@link #update}. */
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    public static final String ALLOW_NEGATIVE_STOCK = "allow_negative_stock";

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);

    private final AppSettingRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /** True when billing may continue after stock reaches zero (stock then goes negative). */
    @Transactional(readOnly = true)
    public boolean isNegativeStockAllowed() {
        return repository.findById(ALLOW_NEGATIVE_STOCK)
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public SettingsResponse current() {
        return repository.findById(ALLOW_NEGATIVE_STOCK)
                .map(AppSettingsService::toResponse)
                .orElseGet(() -> new SettingsResponse(false, null, null));
    }

    @Transactional
    public SettingsResponse update(AuthenticatedUser actor, boolean allowNegativeStock) {
        AppSetting setting = repository.findById(ALLOW_NEGATIVE_STOCK)
                .orElseGet(() -> new AppSetting(ALLOW_NEGATIVE_STOCK, "false", Instant.now(clock), null));
        boolean previous = Boolean.parseBoolean(setting.getValue());

        setting.setValue(Boolean.toString(allowNegativeStock));
        setting.setUpdatedAt(Instant.now(clock));
        setting.setUpdatedBy(actor.id());
        repository.save(setting);

        if (previous != allowNegativeStock) {
            auditLogRepository.save(AuditLog.builder()
                    .actorId(actor.id())
                    .action("CHANGE_SETTING")
                    .entity("AppSetting")
                    .entityId(actor.id())   // settings have no UUID of their own; reference the actor
                    .reasonCode(ALLOW_NEGATIVE_STOCK + "=" + allowNegativeStock)
                    .build());
            log.info("settings.changed key={} value={} actor={}", ALLOW_NEGATIVE_STOCK, allowNegativeStock, actor.id());
        }
        return toResponse(setting);
    }

    private static SettingsResponse toResponse(AppSetting setting) {
        return new SettingsResponse(Boolean.parseBoolean(setting.getValue()), setting.getUpdatedAt(), setting.getUpdatedBy());
    }
}
