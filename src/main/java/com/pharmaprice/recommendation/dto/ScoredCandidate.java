package com.pharmaprice.recommendation.dto;

import java.util.List;

/** ScoreCalculator.rank()의 출력 1건 — 점수와 근거, 뱃지가 포함된 정렬된 후보. */
public record ScoredCandidate(
        Candidate candidate,
        double score,
        ScoreBreakdown breakdown,
        List<Badge> badges
) {
}
