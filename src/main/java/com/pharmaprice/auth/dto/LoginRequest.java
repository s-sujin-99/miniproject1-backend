package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** API.md §2 POST /api/v1/auth/login 요청. */
public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
}
