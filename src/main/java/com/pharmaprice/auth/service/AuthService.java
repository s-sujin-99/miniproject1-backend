package com.pharmaprice.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.domain.AuthProvider;
import com.pharmaprice.auth.dto.LoginRequest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.auth.dto.MeResponse;
import com.pharmaprice.auth.dto.SignupRequest;
import com.pharmaprice.auth.dto.SignupResponse;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.security.JwtTokenProvider;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 가입/로그인/내정보(ROADMAP T-24, API.md §2). Refresh Token은 구현하지 않는다(shrimp-rules.md §5.2). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        AppUser user = AppUser.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .provider(AuthProvider.LOCAL)
                .build();
        return SignupResponse.from(appUserRepository.save(user));
    }

    // 이메일/비밀번호 중 무엇이 틀렸는지 구분하지 않는다(shrimp-rules.md §5.1) — 둘 다 동일한 401 메시지.
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email())
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "이메일 또는 비밀번호가 올바르지 않습니다."));
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        return LoginResponse.of(token, expirationMs / 1000, user);
    }

    public MeResponse getMe(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return MeResponse.from(user);
    }
}
