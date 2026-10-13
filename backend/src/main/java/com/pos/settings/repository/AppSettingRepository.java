package com.pos.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.settings.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
