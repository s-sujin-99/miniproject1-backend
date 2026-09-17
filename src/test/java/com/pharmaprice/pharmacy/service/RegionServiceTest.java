package com.pharmaprice.pharmacy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.pharmacy.repository.RegionRepository;

/** ROADMAP T-14 완료 판정 — API.md §7 시도별 그룹 구조와 pharmacyCount 집계를 실제 DB로 검증한다. */
class RegionServiceTest extends AbstractIntegrationTest {

    @Autowired
    RegionService regionService;
    @Autowired
    RegionRepository regionRepository;
    @Autowired
    PharmacyRepository pharmacyRepository;

    // region.code는 varchar(10) — 테스트마다 겹치지 않는 10자 고정 길이 코드를 카운터로 만든다.
    private static final AtomicInteger CODE_SEQ = new AtomicInteger();

    private Region region(String sido, String sigungu) {
        String code = "T" + String.format("%09d", CODE_SEQ.incrementAndGet());
        return regionRepository.save(Region.builder()
                .code(code).sido(sido).sigungu(sigungu)
                .centerLat(37.5).centerLng(127.0).build());
    }

    // region.sido는 varchar(20) — 짧고 유일한 테스트용 시도명을 카운터로 만든다.
    private String testSido() {
        return "테스트시도" + CODE_SEQ.incrementAndGet();
    }

    @Test
    void 같은_시도의_시군구가_한_그룹으로_묶이고_pharmacyCount가_실제_약국_수와_일치한다() {
        String sido = testSido();
        Region withPharmacy = region(sido, "가구");
        Region withoutPharmacy = region(sido, "나구");
        pharmacyRepository.save(Pharmacy.builder()
                .hiraCode("REG-TEST-" + System.nanoTime()).name("리전테스트약국1")
                .region(withPharmacy).lat(37.5).lng(127.0).build());
        pharmacyRepository.save(Pharmacy.builder()
                .hiraCode("REG-TEST-" + (System.nanoTime() + 1)).name("리전테스트약국2")
                .region(withPharmacy).lat(37.5).lng(127.0).build());

        List<RegionGroupResponse> result = regionService.listGroupedBySido();

        RegionGroupResponse group = result.stream().filter(g -> g.sido().equals(sido)).findFirst().orElseThrow();
        assertThat(group.sigungus()).hasSize(2);
        assertThat(group.sigungus()).anySatisfy(s -> {
            assertThat(s.code()).isEqualTo(withPharmacy.getCode());
            assertThat(s.pharmacyCount()).isEqualTo(2);
        });
        assertThat(group.sigungus()).anySatisfy(s -> {
            assertThat(s.code()).isEqualTo(withoutPharmacy.getCode());
            assertThat(s.pharmacyCount()).isZero();
        });
    }

    @Test
    void centerLat_centerLng는_null이_아니고_region_테이블의_값과_동일하다() {
        String sido = testSido();
        Region r = regionRepository.save(Region.builder()
                .code("T" + String.format("%09d", CODE_SEQ.incrementAndGet())).sido(sido).sigungu("다구")
                .centerLat(35.1234).centerLng(129.5678).build());

        List<RegionGroupResponse> result = regionService.listGroupedBySido();

        RegionGroupResponse group = result.stream().filter(g -> g.sido().equals(sido)).findFirst().orElseThrow();
        assertThat(group.sigungus().get(0).centerLat()).isEqualTo(r.getCenterLat());
        assertThat(group.sigungus().get(0).centerLng()).isEqualTo(r.getCenterLng());
    }
}
