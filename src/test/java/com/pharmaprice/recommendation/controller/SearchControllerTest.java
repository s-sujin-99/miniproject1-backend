package com.pharmaprice.recommendation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;

/**
 * ROADMAP T-15 완료 판정 — API.md §5 GET /api/v1/search를 실제 HTTP 경로(MockMvc)로 검증한다.
 * 후보 3곳은 ScoreCalculatorTest의 "가장 싼 약국이 아니어도 거리 덕분에 1위가 될 수 있다" 시나리오를
 * 재사용해, SCORE 정렬과 PRICE 정렬의 1위가 실제로 달라지는 것을 e2e로 확인한다.
 */
class SearchControllerTest extends AbstractIntegrationTest {

    private static final double USER_LAT = 37.5;
    private static final double USER_LNG = 127.0;
    private static final int RADIUS = 2000;

    @Autowired
    DrugRepository drugRepository;
    @Autowired
    PharmacyRepository pharmacyRepository;
    @Autowired
    PharmacyDrugPriceStatRepository statRepository;
    @Autowired
    RegionRepository regionRepository;

    Drug drug;
    Pharmacy cheapButFar;
    Pharmacy nearButPricier;
    Pharmacy expensiveOutlier;
    Pharmacy noStatPharmacy;

    private Pharmacy pharmacy(String suffix, double deltaLat) {
        return pharmacyRepository.save(Pharmacy.builder()
                .hiraCode("SRCH-" + suffix + "-" + System.nanoTime()).name("검색테스트약국" + suffix)
                .lat(USER_LAT + deltaLat).lng(USER_LNG).build());
    }

    private void stat(Pharmacy pharmacy, int price, int reportCount) {
        statRepository.save(PharmacyDrugPriceStat.builder()
                .pharmacy(pharmacy).drug(drug)
                .repPrice(price).minPrice(price).maxPrice(price).avgPrice(price)
                .reportCount(reportCount).lastReportedAt(LocalDate.now())
                .windowDays((short) 90).calculatedAt(java.time.OffsetDateTime.now())
                .build());
    }

    @BeforeEach
    void setUp() {
        drug = drugRepository.save(Drug.builder()
                .name("검색테스트약품정").displayName("검색테스트약품").category("기타").packageUnit("1개").build());

        cheapButFar = pharmacy("FAR", 0.0175);      // 반경 경계 코앞, 가장 저렴
        nearButPricier = pharmacy("NEAR", 0.0005);  // 아주 가까움, 약간 비쌈
        expensiveOutlier = pharmacy("OUT", 0.0010); // priceScore 폭을 벌리는 역할
        noStatPharmacy = pharmacy("NOST", 0.0008); // 반경 안이지만 이 약품 통계가 없다

        stat(cheapButFar, 1000, 5);
        stat(nearButPricier, 1050, 5);
        stat(expensiveOutlier, 5000, 5);
    }

    @Test
    void 기본_SCORE_정렬은_가격_거리_신선도를_종합해_1위를_고른다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("lat", String.valueOf(USER_LAT))
                        .param("lng", String.valueOf(USER_LNG))
                        .param("radius", String.valueOf(RADIUS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.locationSource").value("GPS"))
                .andExpect(jsonPath("$.summary.resultCount").value(3))
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].pharmacy.id").value(nearButPricier.getId()))
                .andExpect(jsonPath("$.results[0].recommended").value(true))
                .andExpect(jsonPath("$.results[0].scoreBreakdown.weights.price").value(0.6))
                .andExpect(jsonPath("$.results[0].badges").isArray());
    }

    @Test
    void sort을_PRICE로_바꾸면_1위가_실제로_바뀐다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("lat", String.valueOf(USER_LAT))
                        .param("lng", String.valueOf(USER_LNG))
                        .param("sort", "PRICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].pharmacy.id").value(cheapButFar.getId()))
                // recommended는 표시 순서가 아니라 SCORE 1위(nearButPricier)를 계속 가리켜야 한다.
                .andExpect(jsonPath("$.results[0].recommended").value(false));
    }

    @Test
    void 이_약품_통계가_없는_약국은_반경_안에_있어도_결과에서_빠진다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("lat", String.valueOf(USER_LAT))
                        .param("lng", String.valueOf(USER_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.pharmacy.id == " + noStatPharmacy.getId() + ")]").isEmpty());
    }

    @Test
    void regionCode만으로도_검색된다() throws Exception {
        Region region = regionRepository.save(Region.builder()
                .code("TSRCH0001").sido("검색테스트광역시").sigungu("검색구")
                .centerLat(USER_LAT).centerLng(USER_LNG).build());

        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("regionCode", region.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.locationSource").value("REGION"))
                .andExpect(jsonPath("$.summary.resultCount").value(3));
    }

    @Test
    void 위치_정보가_전혀_없으면_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("drugId", String.valueOf(drug.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void drugId가_없으면_400_VALIDATION_FAILED다() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 허용되지_않는_반경이면_400_INVALID_RADIUS다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("lat", "37.5").param("lng", "127.0")
                        .param("radius", "1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RADIUS"));
    }

    @Test
    void 대한민국_범위_밖_좌표면_400_INVALID_COORDINATE다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(drug.getId()))
                        .param("lat", "1.0").param("lng", "1.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATE"));
    }

    @Test
    void 존재하지_않는_약품이면_404_DRUG_NOT_FOUND다() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", "-1")
                        .param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRUG_NOT_FOUND"));
    }

    @Test
    void 후보가_전혀_없으면_결과_0건이고_반경_확대_제안이_채워진다() throws Exception {
        Drug lonelyDrug = drugRepository.save(Drug.builder()
                .name("외딴약품정").displayName("외딴약품").category("기타").packageUnit("1개").build());

        mockMvc.perform(get("/api/v1/search")
                        .param("drugId", String.valueOf(lonelyDrug.getId()))
                        .param("lat", "37.5").param("lng", "127.0")
                        .param("radius", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.resultCount").value(0))
                .andExpect(jsonPath("$.summary.candidateAvgPrice").value(nullValue()))
                .andExpect(jsonPath("$.results", hasSize(0)))
                .andExpect(jsonPath("$.suggestion.type").value("EXPAND_RADIUS"))
                .andExpect(jsonPath("$.suggestion.recommendedRadius").value(1000));
    }
}
