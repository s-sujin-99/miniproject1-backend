package com.pharmaprice.recommendation.dto;

import java.time.LocalDate;

/** ScoreCalculator 입력 — 반경 내 (약국, 약품) 후보 1건. */
public record Candidate(
        long pharmacyId,
        int repPrice,
        double distanceM,
        LocalDate lastReportedAt,
        int reportCount
) {
}
