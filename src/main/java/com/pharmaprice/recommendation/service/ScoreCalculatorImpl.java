package com.pharmaprice.recommendation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.dto.Badge;
import com.pharmaprice.recommendation.dto.Candidate;
import com.pharmaprice.recommendation.dto.ScoreBreakdown;
import com.pharmaprice.recommendation.dto.ScoredCandidate;

import lombok.RequiredArgsConstructor;

/** API.md §5 Score 계산 명세를 그대로 구현한다. 임의로 수식을 바꾸지 않는다(shrimp-rules §4.6). */
@Service
@RequiredArgsConstructor
public class ScoreCalculatorImpl implements ScoreCalculator {

    private final RecommendationProperties properties;

    @Override
    public List<ScoredCandidate> rank(List<Candidate> candidates, int radiusM, LocalDate today) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int minPrice = candidates.stream().mapToInt(Candidate::repPrice).min().orElseThrow();
        int maxPrice = candidates.stream().mapToInt(Candidate::repPrice).max().orElseThrow();
        double minDistance = candidates.stream().mapToDouble(Candidate::distanceM).min().orElseThrow();

        RecommendationProperties.Weights weights = properties.weights();
        ScoreBreakdown.Weights breakdownWeights =
                new ScoreBreakdown.Weights(weights.price(), weights.distance(), weights.freshness());

        List<ScoredCandidate> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            // 후보가 1개뿐이거나 전원 가격이 같으면 min-max 정규화가 0으로 나누기가 되므로 1.0으로 고정한다.
            double priceScore = (maxPrice == minPrice) ? 1.0
                    : (double) (maxPrice - c.repPrice()) / (maxPrice - minPrice);
            double distanceScore = clamp(1 - c.distanceM() / radiusM, 0, 1);
            long ageDays = ChronoUnit.DAYS.between(c.lastReportedAt(), today);
            double freshnessScore = Math.pow(0.5, ageDays / (double) properties.freshnessHalfLifeDays());

            double score = weights.price() * priceScore
                    + weights.distance() * distanceScore
                    + weights.freshness() * freshnessScore;

            ScoreBreakdown breakdown = new ScoreBreakdown(
                    round4(priceScore), round4(distanceScore), round4(freshnessScore), breakdownWeights);

            List<Badge> badges = new ArrayList<>();
            if (c.reportCount() == 1) {
                badges.add(Badge.LOW_CONFIDENCE);
            }
            if (ageDays > 30) {
                badges.add(Badge.STALE_DATA);
            }
            if (c.distanceM() == minDistance) {
                badges.add(Badge.NEAREST);
            }

            scored.add(new ScoredCandidate(c, round4(score), breakdown, badges));
        }

        // score DESC → repPrice ASC → distanceM ASC → pharmacyId ASC. 마지막 타이브레이커가
        // 동일 입력에 대한 결정적 순서를 보장한다(없으면 테스트가 흔들린다).
        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparingInt(sc -> sc.candidate().repPrice())
                .thenComparingDouble(sc -> sc.candidate().distanceM())
                .thenComparingLong(sc -> sc.candidate().pharmacyId()));

        ScoredCandidate top = scored.get(0);
        List<Badge> topBadges = new ArrayList<>(top.badges());
        topBadges.add(0, Badge.LOWEST_PRICE);
        scored.set(0, new ScoredCandidate(top.candidate(), top.score(), top.breakdown(), topBadges));

        return scored;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
