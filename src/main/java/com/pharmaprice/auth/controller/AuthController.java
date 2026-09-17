package com.pharmaprice.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.security.AuthenticatedUser;
import com.pharmaprice.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API.md §2 인증 — shrimp-rules.md §5.2에 따라 refresh 관련 엔드포인트는 두지 않는다. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // 서버에 저장된 refresh 토큰이 없어(refresh 미구현) 무효화할 대상이 없다 — 인증된 요청인지만 확인하고 204.
    // 필터체인이 이미 인증을 강제하므로(anyRequest().authenticated()) 메서드 본문은 비어 있어도 멱등하게 204를 낸다.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.getMe(user.userId());
    }
}
