package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.dto.PriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.repository.PriceReportRepository;

/**
 * DATABASE.md §5.2 절차를 실제 DB(pharmaprice_test)로 검증한다(ROADMAP T-10 완료 판정).
 */
class PriceStatServiceTest extends AbstractIntegrationTest {

    @Autowired
    PriceStatService priceStatService;
    @Autowired
    PharmacyRepository pharmacyRepository;
    @Autowired
    DrugRepository drugRepository;
    @Autowired
    PriceReportRepository priceReportRepository;
    @Autowired
    PharmacyDrugPriceStatRepository statRepository;

    Pharmacy pharmacy;
    Drug drug;

    @BeforeEach
    void setUp() {
        pharmacy = pharmacyRepository.save(Pharmacy.builder()
                .hiraCode("STAT-TEST-" + System.nanoTime()).name("통계테스트약국")
                .lat(37.5).lng(127.0).build());
        drug = drugRepository.save(Drug.builder()
                .name("통계테스트약품").displayName("통계테스트약품").category("기타").packageUnit("1개").build());
    }

    private void report(int price, int daysAgo) {
        priceReportRepository.save(PriceReport.builder()
                .pharmacy(pharmacy).drug(drug).price(price).purchasedAt(LocalDate.now().minusDays(daysAgo))
                .build());
    }

    @Test
    void 극단값_1건이_있어도_5건이면_중앙값이_흔들리지_않는다() {
        report(3000, 1);
        report(3100, 2);
        report(2900, 3);
        report(3050, 4);
        report(50000, 5); // 명백한 오타/이상치

        Optional<PriceStat> stat = priceStatService.recalculate(pharmacy.getId(), drug.getId());

        assertThat(stat).isPresent();
        // IQR로 50000이 제거되면 남은 4건(2900,3000,3050,3100)의 중앙값은 3025 근방이어야 한다.
        assertThat(stat.get().repPrice()).isBetween(2900, 3100);
        assertThat(stat.get().windowDays()).isEqualTo((short) 90);
    }

    @Test
    void 표본이_4건_미만이면_IQR_제거_없이_중앙값을_반환한다() {
        report(1000, 1);
        report(2000, 2);
        report(9000, 3); // 3건뿐이라 이상치처럼 보여도 제거되지 않아야 한다

        Optional<PriceStat> stat = priceStatService.recalculate(pharmacy.getId(), drug.getId());

        assertThat(stat).isPresent();
        assertThat(stat.get().reportCount()).isEqualTo(3);
        assertThat(stat.get().repPrice()).isEqualTo(2000); // 3건의 중앙값
    }

    @Test
    void 구십일_내_제보가_없으면_백팔십일_창으로_확대된다() {
        report(2500, 120); // 90일 밖, 180일 안

        Optional<PriceStat> stat = priceStatService.recalculate(pharmacy.getId(), drug.getId());

        assertThat(stat).isPresent();
        assertThat(stat.get().windowDays()).isEqualTo((short) 180);
        assertThat(stat.get().repPrice()).isEqualTo(2500);
    }

    @Test
    void 유효_제보가_없으면_기존_stat_행이_삭제된다() {
        report(2500, 1);
        priceStatService.recalculate(pharmacy.getId(), drug.getId());
        assertThat(statRepository.findByPharmacyIdAndDrugId(pharmacy.getId(), drug.getId())).isPresent();

        // 유일한 제보를 숨김 처리(HIDDEN)하면 유효 제보가 0건이 된다.
        PriceReport onlyReport = priceReportRepository.findAll().stream()
                .filter(r -> r.getPharmacy().getId().equals(pharmacy.getId()) && r.getDrug().getId().equals(drug.getId()))
                .findFirst().orElseThrow();
        priceReportRepository.delete(onlyReport);

        Optional<PriceStat> stat = priceStatService.recalculate(pharmacy.getId(), drug.getId());

        assertThat(stat).isEmpty();
        assertThat(statRepository.findByPharmacyIdAndDrugId(pharmacy.getId(), drug.getId())).isEmpty();
    }

    @Test
    void 같은_조합을_두_번_재계산해도_결과가_동일하다() {
        report(3000, 1);
        report(3200, 2);
        report(2800, 3);

        Optional<PriceStat> first = priceStatService.recalculate(pharmacy.getId(), drug.getId());
        Optional<PriceStat> second = priceStatService.recalculate(pharmacy.getId(), drug.getId());

        assertThat(first).isEqualTo(second);
        assertThat(statRepository.findByPharmacyIdAndDrugId(pharmacy.getId(), drug.getId())).isPresent();
    }
}
