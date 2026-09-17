package com.pharmaprice.auth.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.AuthProvider;
import com.pharmaprice.auth.repository.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Google 로그인 성공 시 JWT를 발급해 프론트로 리다이렉트한다(ROADMAP T-24, shrimp-rules.md §5.3).
 * 쿼리스트링(?token=)이 아니라 fragment(#token=)를 쓴다 — fragment는 서버 로그·Referer에 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserRepository appUserRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        boolean emailVerified = Boolean.TRUE.equals(oAuth2User.getAttribute("email_verified"));

        Optional<AppUser> existing = appUserRepository.findByEmail(email);
        // 같은 이메일의 LOCAL 계정이 있으면 Google의 email_verified가 true일 때만 그 계정으로 로그인시킨다.
        // 미인증 이메일로 기존 계정을 가로채는 걸 막기 위함 — 이 경우는 실패로 취급해 로그인 실패 페이지로 보낸다.
        if (existing.isPresent() && existing.get().getProvider() == AuthProvider.LOCAL && !emailVerified) {
            response.sendRedirect(frontendUrl + "/login?error=oauth");
            return;
        }
        AppUser user = existing.orElseGet(() -> appUserRepository.save(AppUser.builder()
                .email(email)
                .nickname(name != null ? name : email)
                .provider(AuthProvider.GOOGLE)
                .build()));

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        response.sendRedirect(frontendUrl + "/oauth/callback#token=" + token);
    }
}
