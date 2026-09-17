package com.pharmaprice.auth.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Google 로그인 실패 시 프론트 로그인 화면으로 되돌려보낸다(ROADMAP T-24, shrimp-rules.md §5.3). */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.sendRedirect(frontendUrl + "/login?error=oauth");
    }
}
