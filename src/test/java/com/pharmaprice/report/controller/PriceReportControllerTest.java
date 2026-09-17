package com.pharmaprice.report.controller;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.dto.LoginResponse;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * ROADMAP T-26 완료 판정 — 가격 제보 생성 API를 실제 HTTP 경로로 검증한다.
 * 시드(V2__seed_master.sql)에는 pharmacy_drug_price_stat이 이미 채워져 있어, 그중 한 쌍을 기준으로
 * 정상/중복/이상치 시나리오를 만든다.
 */
class PriceReportControllerTest extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PharmacyDrugPriceStatRepository statRepository;
    @Autowired
    DrugRepository drugRepository;
    @Autowired
    PriceReportRepository priceReportRepository;

    @Test
    void 정상_제보는_201과_갱신된_통계를_반환한다() throws Exception {
        // 시드 stat.reportCount는 시드 생성 시점 기준 90일 창으로 계산된 값이라, 지금 재계산하면
        // (DB가 그 사이 시간이 지나) 다른 수가 나올 수 있다 — 정확한 +1 대신 "반영됐다"만 검증한다.
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        String token = signupAndLogin();

        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), stat.getDrug().getId(), stat.getRepPrice(), null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.flagged").value(false))
                .andExpect(jsonPath("$.updatedStat.reportCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.updatedStat.repPrice").exists());
    }

    @Test
    void 같은_날_같은_약국_약품_재제보는_409_DUPLICATE_REPORT다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        String token = signupAndLogin();
        String body = objectMapper.writeValueAsString(new ReportPayload(
                stat.getPharmacy().getId(), stat.getDrug().getId(), stat.getRepPrice(), null, null, null));

        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"));
    }

    @Test
    void 이상치_제보는_201이고_flagged와_warning이_있으며_대표가격에_영향을_주지_않는다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        int repPriceBefore = stat.getRepPrice();
        String token = signupAndLogin();

        // 허용 최솟값 100원 — 정상 시드 가격(수천 원대)의 0.3배보다 항상 작아 OUTLIER_LOW로 판정된다.
        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), stat.getDrug().getId(), 100, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagged").value(true))
                .andExpect(jsonPath("$.flagReason").value("OUTLIER_LOW"))
                .andExpect(jsonPath("$.warning").exists())
                .andExpect(jsonPath("$.updatedStat.repPrice").value(repPriceBefore));
    }

    @Test
    void 전문의약품_제보시도는_422_DRUG_NOT_OTC다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        // item_seq는 VARCHAR(20)이라 nanoTime을 그대로 이어붙이면 넘친다 — base36으로 줄인다.
        Drug nonOtcDrug = Drug.builder()
                .itemSeq("T" + Long.toString(System.nanoTime(), 36)).name("전문의약품").displayName("전문의약품")
                .category("기타").packageUnit("1정").build();
        ReflectionTestUtils.setField(nonOtcDrug, "otcFlag", false);
        drugRepository.saveAndFlush(nonOtcDrug);
        String token = signupAndLogin();

        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), nonOtcDrug.getId(), 1000, null, null, null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DRUG_NOT_OTC"));
    }

    @Test
    void 제보_직후_검색_결과에_즉시_반영된다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        long pharmacyId = stat.getPharmacy().getId();
        long drugId = stat.getDrug().getId();
        double lat = stat.getPharmacy().getLat();
        double lng = stat.getPharmacy().getLng();
        String token = signupAndLogin();

        MvcResult reportResult = mockMvc.perform(post("/api/v1/price-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReportPayload(pharmacyId, drugId, stat.getRepPrice(), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        int repPriceAfterReport = objectMapper.readTree(reportResult.getResponse().getContentAsString())
                .path("updatedStat").path("repPrice").asInt();

        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drugId))
                        .param("lat", String.valueOf(lat)).param("lng", String.valueOf(lng))
                        .param("radius", "5000").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.pharmacy.id == %d)].price.repPrice", pharmacyId)
                        .value(repPriceAfterReport));
    }

    @Test
    void 목록_조회는_비로그인도_가능하고_reporter에_닉네임만_있다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        String token = signupAndLogin();
        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), stat.getDrug().getId(), stat.getRepPrice(), null, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/price-reports")
                        .param("pharmacyId", String.valueOf(stat.getPharmacy().getId()))
                        .param("drugId", String.valueOf(stat.getDrug().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reporter.nickname").value("제보테스터"))
                .andExpect(jsonPath("$.content[0].reporter.id").doesNotExist())
                .andExpect(jsonPath("$.content[0].reporter.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].pharmacy.name").exists())
                .andExpect(jsonPath("$.content[0].drug.displayName").exists());
    }

    @Test
    void mine_true를_비로그인으로_호출하면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/price-reports").param("mine", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void mine_true는_본인_제보만_반환한다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        String myToken = signupAndLogin();
        String otherToken = signupAndLogin();
        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), stat.getDrug().getId(), stat.getRepPrice(), null, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/price-reports").param("mine", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void mine_true는_본인_HIDDEN_제보도_보이고_공개_목록에는_안_보인다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        String token = signupAndLogin();
        MvcResult reportResult = mockMvc.perform(post("/api/v1/price-reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), stat.getDrug().getId(), stat.getRepPrice(), null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        long reportId = objectMapper.readTree(reportResult.getResponse().getContentAsString()).path("id").asLong();

        // T-32(관리자 숨김 API)가 아직 없어, 관리자가 숨김 처리한 상태를 테스트에서 직접 만든다.
        var hidden = priceReportRepository.findById(reportId).orElseThrow();
        ReflectionTestUtils.setField(hidden, "status", ReportStatus.HIDDEN);
        priceReportRepository.saveAndFlush(hidden);

        mockMvc.perform(get("/api/v1/price-reports").param("mine", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].status", reportId).value("HIDDEN"));

        mockMvc.perform(get("/api/v1/price-reports")
                        .param("pharmacyId", String.valueOf(stat.getPharmacy().getId()))
                        .param("drugId", String.valueOf(stat.getDrug().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]", reportId).isEmpty());
    }

    @Test
    void 제보_성공시_내_reportCount가_1_증가한다() throws Exception {
        PharmacyDrugPriceStat stat = statRepository.findAll().getFirst();
        String token = signupAndLogin();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.reportCount").value(0));

        mockMvc.perform(post("/api/v1/price-reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPayload(
                                stat.getPharmacy().getId(), stat.getDrug().getId(), stat.getRepPrice(), null, null, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.reportCount").value(1));
    }

    private String signupAndLogin() throws Exception {
        String email = "report-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "제보테스터"))))
                .andExpect(status().isCreated());
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "Password12"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class)
                .accessToken();
        assertThat(token).isNotBlank();
        return token;
    }

    private record SignupPayload(String email, String password, String nickname) {
    }

    private record LoginPayload(String email, String password) {
    }

    private record ReportPayload(Long pharmacyId, Long drugId, Integer price, String purchasedAt,
            Long receiptFileId, String memo) {
    }
}
