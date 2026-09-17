package com.pharmaprice.auth.dto;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.UserRole;

/**
 * API.md §2 POST /api/v1/auth/login 응답 — shrimp-rules.md §5.2에 따라 원문의 refreshToken 필드는 뺀다
 * (Access Token 24시간 단일, Refresh Token 미구현).
 */
public record LoginResponse(String accessToken, long expiresIn, UserSummary user) {

    public static LoginResponse of(String accessToken, long expiresIn, AppUser user) {
        return new LoginResponse(accessToken, expiresIn, new UserSummary(user.getId(), user.getNickname(), user.getRole()));
    }

    public record UserSummary(Long id, String nickname, UserRole role) {
    }
}
