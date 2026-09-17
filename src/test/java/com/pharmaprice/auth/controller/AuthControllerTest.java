package com.pharmaprice.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.dto.LoginResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * ROADMAP T-24 완료 판정 — 가입→로그인→/me→로그아웃 흐름과 시드 admin 계정 로그인을 실제 HTTP 경로로 검증한다.
 */
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 가입_로그인_내정보_로그아웃_흐름이_전부_성공한다() throws Exception {
        String email = "flow-" + System.nanoTime() + "@example.com";
        String signupBody = objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "플로우테스터"));

        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$..passwordHash").doesNotExist());

        String loginBody = objectMapper.writeValueAsString(new LoginPayload(email, "Password12"));
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andReturn();
        String token = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class).accessToken();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.reportCount").value(0))
                .andExpect(jsonPath("$..passwordHash").doesNotExist());

        // 로그아웃은 멱등해야 한다 — 두 번 호출해도 둘 다 204.
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void 이미_가입된_이메일이면_409_EMAIL_ALREADY_EXISTS다() throws Exception {
        String email = "dup-" + System.nanoTime() + "@example.com";
        String body = objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "중복테스터"));

        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 이메일과_비밀번호_중_틀린_쪽을_구분하지_않고_401이다() throws Exception {
        String email = "wrongpw-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "오답테스터"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload("no-such-" + System.nanoTime() + "@example.com", "Password12"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // 시드(V2__seed_master.sql)로 들어간 admin@example.com — 원문 비밀번호는 tools/seed-generator/build_master_seed.py 주석에 있다.
    @Test
    void 시드_admin_계정이_로그인되고_role이_ADMIN이다() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload("admin@example.com", "Admin1234!"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$..passwordHash").doesNotExist());
    }

    @Test
    void 짧은_비밀번호로_가입하면_400_VALIDATION_FAILED다() throws Exception {
        String body = objectMapper.writeValueAsString(new SignupPayload("short-" + System.nanoTime() + "@example.com", "abc", "짧은비번"));

        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 토큰_없이_me를_호출하면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private record SignupPayload(String email, String password, String nickname) {
    }

    private record LoginPayload(String email, String password) {
    }
}
