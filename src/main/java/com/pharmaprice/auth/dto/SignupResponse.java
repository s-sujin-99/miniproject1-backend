package com.pharmaprice.auth.dto;

import java.time.OffsetDateTime;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

/** API.md §2 POST /api/v1/auth/signup 응답. passwordHash는 절대 포함하지 않는다. */
public record SignupResponse(Long id, String email, String nickname, UserRole role, OffsetDateTime createdAt) {

    public static SignupResponse from(AppUser user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getCreatedAt());
    }
}
