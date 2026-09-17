package com.pharmaprice.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.dto.Badge;
import com.pharmaprice.recommendation.dto.Candidate;
import com.pharmaprice.recommendation.dto.ScoredCandidate;

/**
 * 가격·거리·신선도 각 요인이 순위에 미치는 영향을 고정한다(ROADMAP T-12).
 * 절대 점수값이 아니라 상대 순위/부등호로 검증한다 — 가중치를 튜닝해도 테스트가 의미를 유지해야 한다.
 */
class ScoreCalculatorTest {

    private static final RecommendationProperties PROPERTIES = new RecommendationProperties(
            new RecommendationProperties.Weights(0.60, 0.25, 0.15),
            30, 90, 180,
            new RecommendationProperties.Outlier(1.5, 4));

    private static final int RADIUS_M = 2000;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    private final ScoreCalculator calculator = new ScoreCalculatorImpl(PROPERTIES);

    private Candidate candidate(long pharmacyId, int price, double distanceM, LocalDate lastReportedAt, int reportCount) {
        return new Candidate(pharmacyId, price, distanceM, lastReportedAt, reportCount);
    }

    @Test
    void 거리와_신선도가_같으면_가격이_싼_쪽이_상위다() {
        Candidate cheap = candidate(1, 1000, 500, TODAY, 5);
        Candidate expensive = candidate(2, 2000, 500, TODAY, 5);

        List<ScoredCandidate> ranked = calculator.rank(List.of(expensive, cheap), RADIUS_M, TODAY);

        assertThat(ranked.get(0).candidate().pharmacyId()).isEqualTo(1);
    }

    @Test
    void 가격과_신선도가_같으면_가까운_쪽이_상위다() {
        Candidate near = candidate(1, 1000, 100, TODAY, 5);
        Candidate far = candidate(2, 1000, 1500, TODAY, 5);

        List<ScoredCandidate> ranked = calculator.rank(List.of(far, near), RADIUS_M, TODAY);

        assertThat(ranked.get(0).candidate().pharmacyId()).isEqualTo(1);
    }

    @Test
    void 가격과_거리가_같으면_최근_제보_쪽이_상위다() {
        Candidate fresh = candidate(1, 1000, 500, TODAY, 5);
        Candidate stale = candidate(2, 1000, 500, TODAY.minusDays(60), 5);

        List<ScoredCandidate> ranked = calculator.rank(List.of(stale, fresh), RADIUS_M, TODAY);

        assertThat(ranked.get(0).candidate().pharmacyId()).isEqualTo(1);
    }

    @Test
    void 후보가_1개면_예외_없이_priceScore가_1이다() {
        List<ScoredCandidate> ranked = calculator.rank(
                List.of(candidate(1, 1500, 500, TODAY, 3)), RADIUS_M, TODAY);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).breakdown().priceScore()).isEqualTo(1.0);
    }

    @Test
    void 모든_후보_가격이_같으면_전원_priceScore가_1이다() {
        List<Candidate> candidates = List.of(
                candidate(1, 1000, 100, TODAY, 3),
                candidate(2, 1000, 800, TODAY, 3),
                candidate(3, 1000, 1500, TODAY.minusDays(10), 3));

        List<ScoredCandidate> ranked = calculator.rank(candidates, RADIUS_M, TODAY);

        assertThat(ranked).allSatisfy(sc -> assertThat(sc.breakdown().priceScore()).isEqualTo(1.0));
    }

    @Test
    void 동일_입력을_두_번_호출해도_순서가_완전히_같다() {
        List<Candidate> candidates = List.of(
                candidate(3, 1000, 500, TODAY, 3),
                candidate(1, 1000, 500, TODAY, 3),
                candidate(2, 1000, 500, TODAY, 3));

        List<Long> firstOrder = calculator.rank(candidates, RADIUS_M, TODAY).stream()
                .map(sc -> sc.candidate().pharmacyId()).toList();
        List<Long> secondOrder = calculator.rank(candidates, RADIUS_M, TODAY).stream()
                .map(sc -> sc.candidate().pharmacyId()).toList();

        assertThat(firstOrder).isEqualTo(secondOrder);
        // 완전히 동점인 입력이므로 마지막 타이브레이커(pharmacyId ASC)로만 순서가 결정되어야 한다.
        assertThat(firstOrder).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 가장_싼_약국이_아니어도_거리_덕분에_1위가_될_수_있다() {
        // 두 후보만 있으면 priceScore가 항상 0과 1로 벌어져 거리(가중치 0.25)로는
        // 가격(가중치 0.60)을 절대 역전할 수 없다. 그래서 가격 폭을 넓히는 3번째 후보를 더해
        // "가장 싸지만 먼 곳"과 "약간 비싸지만 아주 가까운 곳"의 priceScore 격차를 좁힌다.
        Candidate cheapButFar = candidate(1, 1000, 1999, TODAY, 5);   // 반경 경계 코앞
        Candidate nearButPricier = candidate(2, 1050, 50, TODAY, 5); // 아주 가까움
        Candidate expensiveOutlier = candidate(3, 5000, 100, TODAY, 5); // priceScore 폭을 벌리는 역할

        List<ScoredCandidate> ranked = calculator.rank(
                List.of(cheapButFar, nearButPricier, expensiveOutlier), RADIUS_M, TODAY);

        // "제일 싼 곳이 1위가 아닌 게 버그 아니냐"는 질문의 답: 의도된 동작이다.
        assertThat(ranked.get(0).candidate().pharmacyId()).isEqualTo(2);
        assertThat(ranked.get(0).badges()).contains(Badge.LOWEST_PRICE);
    }

    @Test
    void 제보_1건이고_30일_초과면_LOW_CONFIDENCE와_STALE_DATA_뱃지가_붙는다() {
        Candidate candidate = candidate(1, 1000, 500, TODAY.minusDays(40), 1);

        List<ScoredCandidate> ranked = calculator.rank(List.of(candidate), RADIUS_M, TODAY);

        assertThat(ranked.get(0).badges()).contains(Badge.LOW_CONFIDENCE, Badge.STALE_DATA);
    }
}
