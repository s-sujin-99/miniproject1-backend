package com.pharmaprice.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * ROADMAP T-23 완료 판정 — JWT 인프라와 URL별 접근 제어를 실제 HTTP 경로(MockMvc)로 검증한다.
 * POST /price-reports, /admin/** 컨트롤러는 아직 없지만(T-24/T-26/T-31에서 구현), Security 필터 체인의
 * authorizeHttpRequests는 핸들러 매핑보다 먼저 동작하므로 401/403 판정에는 영향이 없다.
 */
class JwtSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    DrugRepository drugRepository;
    @Value("${jwt.secret}")
    String jwtSecret;

    @Test
    void 토큰_없이_보호된_POST_경로를_호출하면_401_UNAUTHENTICATED다() throws Exception {
        mockMvc.perform(post("/api/v1/price-reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void USER_토큰으로_admin_경로를_호출하면_403_FORBIDDEN이다() throws Exception {
        String token = jwtTokenProvider.generateToken(1L, "user@test.com", UserRole.USER);

        mockMvc.perform(get("/api/v1/admin/stats/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 만료된_토큰으로_호출하면_401이다() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("1")
                .claim("email", "user@test.com")
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 100_000))
                .expiration(new Date(System.currentTimeMillis() - 50_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(post("/api/v1/price-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 토큰_없이_GET_search는_200이다() throws Exception {
        Drug drug = drugRepository.save(Drug.builder()
                .name("보안테스트약품정").displayName("보안테스트약품").category("기타").packageUnit("1개").build());

        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isOk());
    }
}
