package com.shopsmart.activity.dto;

import jakarta.validation.constraints.NotNull;

/** Client-reported browsing signal. Only CATEGORY_VIEW is accepted from clients; other events are recorded server-side. */
public record ActivityRequest(@NotNull Long categoryId) {
}
