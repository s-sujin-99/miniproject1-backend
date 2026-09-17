package com.pharmaprice.recommendation.dto;

/** 순위 근거(API.md §5 scoreBreakdown) — 관리자·디버깅용이지만 항상 채워 설명 가능성을 확보한다. */
public record ScoreBreakdown(
        double priceScore,
        double distanceScore,
        double freshnessScore,
        Weights weights
) {
    public record Weights(double price, double distance, double freshness) {
    }
}
