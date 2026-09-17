package com.pharmaprice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API.md §2 POST /api/v1/auth/signup 요청. 비밀번호 규칙은 shrimp-rules.md §5.1로 축소(길이만 검사). */
public record SignupRequest(
        @Email @NotBlank String email,
        @Size(min = 6, max = 64) @NotBlank String password,
        @Size(min = 2, max = 30) @NotBlank String nickname
) {
}
