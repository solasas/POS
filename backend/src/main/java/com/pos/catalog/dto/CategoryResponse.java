package com.pos.catalog.dto;

import java.util.UUID;

import com.pos.catalog.entity.Category;

public record CategoryResponse(UUID id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
