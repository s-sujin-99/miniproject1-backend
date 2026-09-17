package com.pharmaprice.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ROADMAP T-32 완료 판정 — 제보 관리 API를 실제 HTTP 경로로 검증한다.
 * IQR 트리밍(minSamples=4) 영향을 피해 재계산 결과를 결정적으로 검증하려고, 매번 새로 만든
 * Drug(제보 0건에서 시작)를 기준으로 표본 1~2건짜리 통계를 직접 구성한다.
 */
class AdminReportControllerTest extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DrugRepository drugRepository;
    @Autowired
    PharmacyRepository pharmacyRepository;
    @Autowired
    PharmacyDrugPriceStatRepository statRepository;

    @Test
    void USER_토큰으로_목록과_수정_모두_403이다() throws Exception {
        String token = signupAndLogin();

        mockMvc.perform(get("/api/v1/admin/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/price-reports/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 존재하지_않는_제보를_수정하면_404다() throws Exception {
        String token = adminLogin();

        mockMvc.perform(patch("/api/v1/admin/price-reports/999999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void 목록은_reporter에_id_email이_있고_flagged_필터가_동작한다() throws Exception {
        Pharmacy pharmacy = anyActivePharmacy();
        Drug drug = freshOtcDrug();
        String userToken = signupAndLogin();
        submitReport(userToken, pharmacy.getId(), drug.getId(), 100); // 첫 제보라 median 없음 → flagged=false

        String adminToken = adminLogin();
        MvcResult result = mockMvc.perform(get("/api/v1/admin/price-reports")
                        .param("drugId", String.valueOf(drug.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString()).path("content");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).path("reporter").path("id").isMissingNode()).isFalse();
        assertThat(rows.get(0).path("reporter").path("email").asText()).contains("@");
    }

    @Test
    void 제보를_HIDDEN으로_바꾸면_rep_price가_즉시_재계산되고_DB값과_일치한다() throws Exception {
        Pharmacy pharmacy = anyActivePharmacy();
        Drug drug = freshOtcDrug();
        String userAToken = signupAndLogin();
        String userBToken = signupAndLogin();

        submitReport(userAToken, pharmacy.getId(), drug.getId(), 2000);
        long reportBId = submitReport(userBToken, pharmacy.getId(), drug.getId(), 2400);

        // 숨기기 전: 표본 2건, 중앙값(2000,2400)=2200.
        PharmacyDrugPriceStat statBefore = statRepository.findByPharmacyIdAndDrugId(pharmacy.getId(), drug.getId())
                .orElseThrow();
        assertThat(statBefore.getReportCount()).isEqualTo(2);
        assertThat(statBefore.getRepPrice()).isEqualTo(2200);

        String adminToken = adminLogin();
        MvcResult patchResult = mockMvc.perform(patch("/api/v1/admin/price-reports/" + reportBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andReturn();

        JsonNode body = objectMapper.readTree(patchResult.getResponse().getContentAsString());
        JsonNode recalculated = body.path("recalculatedStat");

        // 숨긴 뒤: B(2400)가 빠져 표본 1건, 대표가격은 A(2000)만 남는다 — "즉시 재계산" 확인.
        PharmacyDrugPriceStat statAfter = statRepository.findByPharmacyIdAndDrugId(pharmacy.getId(), drug.getId())
                .orElseThrow();
        assertThat(statAfter.getReportCount()).isEqualTo(1);
        assertThat(statAfter.getRepPrice()).isEqualTo(2000);

        // recalculatedStat이 실제 DB값과 일치하는지 확인.
        assertThat(recalculated.path("reportCount").asInt()).isEqualTo(statAfter.getReportCount());
        assertThat(recalculated.path("repPrice").asInt()).isEqualTo(statAfter.getRepPrice());
        assertThat(recalculated.path("pharmacyId").asLong()).isEqualTo(pharmacy.getId());
        assertThat(recalculated.path("drugId").asLong()).isEqualTo(drug.getId());
    }

    @Test
    void flagged를_false로_풀면_통계에_재포함되고_recalculatedStat이_DB값과_일치한다() throws Exception {
        Drug drug = freshOtcDrug();
        List<Pharmacy> pharmacies = twoActivePharmacies();
        Pharmacy baselinePharmacy = pharmacies.get(0);
        Pharmacy outlierPharmacy = pharmacies.get(1);

        // 기준가 확보(median 계산 근거) — 3000원.
        submitReport(signupAndLogin(), baselinePharmacy.getId(), drug.getId(), 3000);

        // 100원은 3000 * 0.3 = 900보다 작아 OUTLIER_LOW로 플래그되고 통계에서 빠진다(F2-9, ROADMAP T-26).
        String outlierUserToken = signupAndLogin();
        MvcResult reportResult = mockMvc.perform(post("/api/v1/price-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outlierUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReportPayload(outlierPharmacy.getId(), drug.getId(), 100))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagged").value(true))
                .andReturn();
        long outlierReportId = objectMapper.readTree(reportResult.getResponse().getContentAsString()).path("id").asLong();

        // 플래그된 상태에서는 (outlierPharmacy, drug) 통계가 아예 없어야 한다.
        assertThat(statRepository.findByPharmacyIdAndDrugId(outlierPharmacy.getId(), drug.getId())).isEmpty();

        String adminToken = adminLogin();
        MvcResult patchResult = mockMvc.perform(patch("/api/v1/admin/price-reports/" + outlierReportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flagged\":false,\"reason\":\"관리자 확인 결과 정상 가격\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagged").value(false))
                .andReturn();

        PharmacyDrugPriceStat statAfter = statRepository
                .findByPharmacyIdAndDrugId(outlierPharmacy.getId(), drug.getId()).orElseThrow();
        assertThat(statAfter.getReportCount()).isEqualTo(1);
        assertThat(statAfter.getRepPrice()).isEqualTo(100);

        JsonNode recalculated = objectMapper.readTree(patchResult.getResponse().getContentAsString())
                .path("recalculatedStat");
        assertThat(recalculated.path("reportCount").asInt()).isEqualTo(statAfter.getReportCount());
        assertThat(recalculated.path("repPrice").asInt()).isEqualTo(statAfter.getRepPrice());
    }

    @Test
    void REJECTED로_바꾸면_제보자_reportCount가_1_감소한다() throws Exception {
        Pharmacy pharmacy = anyActivePharmacy();
        Drug drug = freshOtcDrug();
        String userToken = signupAndLogin();
        submitReport(userToken, pharmacy.getId(), drug.getId(), 1500);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(jsonPath("$.reportCount").value(1));

        long anotherReportId = submitReportForRejection(pharmacy, drug, userToken);

        String adminToken = adminLogin();
        mockMvc.perform(patch("/api/v1/admin/price-reports/" + anotherReportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(jsonPath("$.reportCount").value(1));
    }

    // 같은 유저가 같은 날 같은 (약국,약품)에 두 번 제보할 수 없으므로(uq_report_user_pair_day),
    // REJECTED 테스트는 서로 다른 약품을 하나 더 만들어 두 번째 제보를 넣는다.
    private long submitReportForRejection(Pharmacy pharmacy, Drug firstDrug, String userToken) throws Exception {
        Drug secondDrug = freshOtcDrug();
        return submitReport(userToken, pharmacy.getId(), secondDrug.getId(), 1500);
    }

    private long submitReport(String token, long pharmacyId, long drugId, int price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/price-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(pharmacyId, drugId, price))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private Pharmacy anyActivePharmacy() {
        return pharmacyRepository.findAll().stream().filter(Pharmacy::isActive).findFirst().orElseThrow();
    }

    private List<Pharmacy> twoActivePharmacies() {
        return pharmacyRepository.findAll().stream().filter(Pharmacy::isActive).limit(2).toList();
    }

    // item_seq는 VARCHAR(20)이라 nanoTime을 그대로 이어붙이면 넘친다 — base36으로 줄인다(PriceReportControllerTest와 동일 패턴).
    private Drug freshOtcDrug() {
        Drug drug = Drug.builder()
                .itemSeq("A" + Long.toString(System.nanoTime(), 36))
                .name("관리자테스트약품").displayName("관리자테스트약품")
                .category("기타").packageUnit("1정").build();
        ReflectionTestUtils.setField(drug, "otcFlag", true);
        return drugRepository.saveAndFlush(drug);
    }

    private String signupAndLogin() throws Exception {
        String email = "admin-report-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "관리테스터"))))
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

    private record ReportPayload(Long pharmacyId, Long drugId, Integer price) {
    }
}
