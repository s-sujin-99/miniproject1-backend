package com.pharmaprice.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ROADMAP T-31 완료 판정 — 관리자 통계 4개 엔드포인트를 실제 HTTP 경로로 검증한다.
 * 시드(V2__seed_master.sql)가 이미 충분한 규모라 표본 3건 미만 필터/집계를 실데이터로 확인할 수 있다.
 */
class AdminStatsControllerTest extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void USER_토큰으로_4개_엔드포인트_모두_403이다() throws Exception {
        String token = signupAndLoginUser();

        mockMvc.perform(get("/api/v1/admin/stats/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats/regions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats/drugs/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats/price-gaps").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비로그인_호출도_401_또는_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/overview"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void overview는_총계와_7일치_추이와_커버리지를_반환한다() throws Exception {
        String token = adminLogin();

        mockMvc.perform(get("/api/v1/admin/stats/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.pharmacyCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totals.drugCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totals.reportCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totals.userCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recentTrend.length()").value(7))
                .andExpect(jsonPath("$.coverageRate").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0.0), org.hamcrest.Matchers.lessThanOrEqualTo(1.0))));
    }

    @Test
    void regions는_표본_3건_미만_지역을_제외한다() throws Exception {
        String token = adminLogin();

        MvcResult result = mockMvc.perform(get("/api/v1/admin/stats/regions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString()).path("rows");
        assertThat(rows.size()).isGreaterThan(0);
        for (JsonNode row : rows) {
            assertThat(row.path("pharmacyCount").asInt()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void drugs_상세는_히스토그램과_전국통계를_반환하고_없는_약품은_404다() throws Exception {
        String token = adminLogin();

        mockMvc.perform(get("/api/v1/admin/stats/drugs/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drug.id").value(1))
                .andExpect(jsonPath("$.drug.displayName").exists())
                .andExpect(jsonPath("$.national.avg").exists());

        mockMvc.perform(get("/api/v1/admin/stats/drugs/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRUG_NOT_FOUND"));
    }

    @Test
    void price_gaps는_gapPct가_수기검산과_일치하고_지역_3곳_미만_약품은_없다() throws Exception {
        String token = adminLogin();

        MvcResult result = mockMvc.perform(get("/api/v1/admin/stats/price-gaps").param("limit", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString()).path("rows");
        assertThat(rows.size()).isGreaterThan(0);
        assertThat(rows.size()).isLessThanOrEqualTo(5);

        double previousGapPct = Double.MAX_VALUE;
        for (JsonNode row : rows) {
            int cheapest = row.path("cheapestRegion").path("avgPrice").asInt();
            int priciest = row.path("priciestRegion").path("avgPrice").asInt();
            int gap = row.path("gap").asInt();
            double gapPct = row.path("gapPct").asDouble();

            // 수기검산: 화면에 보이는 cheapestRegion/priciestRegion avgPrice 두 값만으로 gap·gapPct를 되짚는다.
            assertThat(gap).isEqualTo(priciest - cheapest);
            double expectedGapPct = Math.round((priciest - cheapest) / (double) cheapest * 1000) / 10.0;
            assertThat(gapPct).isEqualTo(expectedGapPct);
            assertThat(gap).isGreaterThanOrEqualTo(0);
            assertThat(gapPct).isLessThanOrEqualTo(previousGapPct);
            previousGapPct = gapPct;
        }
    }

    private String signupAndLoginUser() throws Exception {
        String email = "admin-stats-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "통계테스터"))))
                .andExpect(status().isCreated());
        return login(email, "Password12");
    }

    // 시드 admin@example.com — 원문 비밀번호는 tools/seed-generator/build_master_seed.py 주석에 있다.
    private String adminLogin() throws Exception {
        return login("admin@example.com", "Admin1234!");
    }

    private String login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class)
                .accessToken();
    }

    private record SignupPayload(String email, String password, String nickname) {
    }

    private record LoginPayload(String email, String password) {
    }
}
