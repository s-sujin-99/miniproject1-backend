package com.pharmaprice.drug.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;

/** ROADMAP T-13 완료 판정 — API.md §3 응답 필드와 검색/상세 동작을 실제 DB(pharmaprice_test)로 검증한다. */
class DrugServiceTest extends AbstractIntegrationTest {

    @Autowired
    DrugService drugService;
    @Autowired
    DrugRepository drugRepository;
    @Autowired
    PharmacyRepository pharmacyRepository;
    @Autowired
    PharmacyDrugPriceStatRepository statRepository;

    @Test
    void q로_검색하면_일치하는_약품이_가격통계와_함께_반환된다() {
        Drug drug = drugRepository.save(Drug.builder()
                .name("드럭검색테스트정500밀리그람").displayName("드럭검색테스트 500mg")
                .category("해열진통").packageUnit("8정").build());
        Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
                .hiraCode("DRUG-TEST-" + System.nanoTime()).name("드럭검색테스트약국")
                .lat(37.5).lng(127.0).build());
        statRepository.save(PharmacyDrugPriceStat.builder()
                .pharmacy(pharmacy).drug(drug)
                .repPrice(3000).minPrice(2800).maxPrice(3200).avgPrice(3000)
                .reportCount(5).lastReportedAt(LocalDate.now())
                .windowDays((short) 90).calculatedAt(OffsetDateTime.now())
                .build());

        PageResponse<DrugSummaryResponse> result =
                drugService.search("드럭검색테스트", null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        DrugSummaryResponse summary = result.content().get(0);
        assertThat(summary.id()).isEqualTo(drug.getId());
        assertThat(summary.nationalAvgPrice()).isEqualTo(3000);
        assertThat(summary.pharmacyCount()).isEqualTo(1);
    }

    @Test
    void 가격_통계가_없는_약품도_검색결과에_포함되고_평균가는_null이다() {
        drugRepository.save(Drug.builder()
                .name("통계없는드럭검색테스트정").displayName("통계없는드럭검색테스트")
                .category("기타").packageUnit("1개").build());

        PageResponse<DrugSummaryResponse> result =
                drugService.search("통계없는드럭검색테스트", null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).nationalAvgPrice()).isNull();
        assertThat(result.content().get(0).pharmacyCount()).isZero();
    }

    @Test
    void 상세조회는_priceStats를_포함한다() {
        Drug drug = drugRepository.save(Drug.builder()
                .name("드럭상세테스트정").displayName("드럭상세테스트")
                .category("기타").packageUnit("1개").build());

        DrugDetailResponse detail = drugService.getDetail(drug.getId());

        assertThat(detail.id()).isEqualTo(drug.getId());
        assertThat(detail.priceStats()).isNotNull();
        assertThat(detail.priceStats().pharmacyCount()).isZero();
    }

    @Test
    void 존재하지_않는_약품이면_DRUG_NOT_FOUND_예외가_발생한다() {
        assertThatThrownBy(() -> drugService.getDetail(-1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DRUG_NOT_FOUND);
    }

    @Test
    void size는_20으로_기본_페이지_정보가_채워진다() {
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<DrugSummaryResponse> result = drugService.search(null, null, pageable);

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(0);
    }
}
