package com.pharmaprice.auth.dto;

import java.time.OffsetDateTime;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

/** API.md §2 GET /api/v1/auth/me 응답. passwordHash는 절대 포함하지 않는다. */
public record MeResponse(Long id, String email, String nickname, UserRole role, int reportCount, OffsetDateTime createdAt) {

    public static MeResponse from(AppUser user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole(),
                user.getReportCount(), user.getCreatedAt());
    }
}
