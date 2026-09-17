package com.pharmaprice.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.AuthProvider;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.repository.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ROADMAP T-24 완료 판정 — 실제 Google 콘솔 계정 없이는 브라우저로 로그인 동의 화면을 통과할 수 없어(외부
 * 서비스 로그인은 자동화가 대행할 수 없는 영역), 콜백이 fragment(#token=)로 JWT를 전달하는 로직 자체를
 * OAuth2User를 직접 구성해 단위 테스트로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:3000";
    private static final String SECRET = "test-secret-at-least-32-bytes-long!!";

    @Mock
    AppUserRepository appUserRepository;
    @Mock
    HttpServletRequest request;
    @Mock
    HttpServletResponse response;
    @Mock
    Authentication authentication;

    private OAuth2SuccessHandler handler() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 86_400_000L);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(appUserRepository, jwtTokenProvider);
        ReflectionTestUtils.setField(handler, "frontendUrl", FRONTEND_URL);
        return handler;
    }

    private OAuth2User googleUser(String email, boolean emailVerified) {
        return new DefaultOAuth2User(java.util.List.of(),
                Map.of("sub", "google-1", "email", email, "email_verified", emailVerified, "name", "구글사용자"),
                "sub");
    }

    @Test
    void 처음_로그인하는_구글_사용자는_계정을_생성하고_fragment로_토큰을_전달한다() throws Exception {
        String email = "new-google-user@example.com";
        when(authentication.getPrincipal()).thenReturn(googleUser(email, true));
        when(appUserRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", 99L);
            return toSave;
        });

        handler().onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<AppUser> savedCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(savedCaptor.getValue().getEmail()).isEqualTo(email);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).startsWith(FRONTEND_URL + "/oauth/callback#token=");
        // 쿼리스트링이 아니라 fragment(#)로 전달돼야 한다 — 로그·Referer에 남지 않게 하기 위함(shrimp-rules §5.3).
        assertThat(redirectUrl).doesNotContain("?token=");
    }

    @Test
    void 이메일이_인증된_구글_로그인은_기존_LOCAL_계정으로_연결된다() throws Exception {
        String email = "existing-local@example.com";
        AppUser existing = AppUser.builder().email(email).passwordHash("hash").nickname("기존유저")
                .role(UserRole.USER).provider(AuthProvider.LOCAL).build();
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(authentication.getPrincipal()).thenReturn(googleUser(email, true));
        when(appUserRepository.findByEmail(email)).thenReturn(Optional.of(existing));

        handler().onAuthenticationSuccess(request, response, authentication);

        verify(appUserRepository, never()).save(any());
        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).startsWith(FRONTEND_URL + "/oauth/callback#token=");
    }

    @Test
    void 이메일_미인증_구글_로그인이_기존_LOCAL_계정과_이메일이_겹치면_로그인_실패_페이지로_보낸다() throws Exception {
        String email = "unverified@example.com";
        AppUser existing = AppUser.builder().email(email).passwordHash("hash").nickname("기존유저")
                .role(UserRole.USER).provider(AuthProvider.LOCAL).build();
        when(authentication.getPrincipal()).thenReturn(googleUser(email, false));
        when(appUserRepository.findByEmail(email)).thenReturn(Optional.of(existing));

        handler().onAuthenticationSuccess(request, response, authentication);

        verify(appUserRepository, never()).save(any());
        verify(response).sendRedirect(FRONTEND_URL + "/login?error=oauth");
    }
}
