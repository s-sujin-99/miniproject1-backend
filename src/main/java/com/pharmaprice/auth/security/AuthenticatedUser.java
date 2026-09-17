package com.pharmaprice.auth.security;

import com.pharmaprice.auth.domain.UserRole;

/**
 * JwtAuthenticationFilter가 SecurityContext에 심는 인증 주체(ROADMAP T-23).
 * 컨트롤러는 {@code @AuthenticationPrincipal AuthenticatedUser} 로만 currentUserId를 얻는다(shrimp-rules.md §4.4).
 */
public record AuthenticatedUser(Long userId, String email, UserRole role) {
}
