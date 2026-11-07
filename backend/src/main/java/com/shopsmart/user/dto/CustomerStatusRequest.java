package com.shopsmart.user.dto;

import com.shopsmart.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record CustomerStatusRequest(@NotNull UserStatus status) {
}
