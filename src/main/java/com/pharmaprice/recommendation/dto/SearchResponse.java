package com.pharmaprice.recommendation.dto;

import java.time.LocalDate;
import java.util.List;

/** API.md §5 GET /api/v1/search 응답. */
public record SearchResponse(
        DrugSummary drug,
        QueryEcho query,
        Summary summary,
        DataSourceType dataSource,
        List<ResultItem> results,
        Suggestion suggestion
) {

    public record DrugSummary(Long id, String displayName, String packageUnit, String imageUrl) {
    }

    public record QueryEcho(double lat, double lng, int radius, SortOption sort, LocationSource locationSource) {
    }

    /** 결과가 0건이면 가격 필드는 전부 null이다(API.md §5 빈 결과 예시). */
    public record Summary(int resultCount, Integer candidateAvgPrice, Integer candidateMinPrice,
                           Integer candidateMaxPrice, Integer maxSaving) {
    }

    public record ResultItem(int rank, boolean recommended, PharmacySummary pharmacy, PriceInfo price,
                              int distanceM, double score, ScoreBreakdown scoreBreakdown, List<Badge> badges) {
    }

    public record PharmacySummary(long id, String name, String addressRoad, double lat, double lng, String phone) {
    }

    public record PriceInfo(int repPrice, int minPrice, int avgPrice, int savingVsCandidateAvg, int reportCount,
                             LocalDate lastReportedAt, long daysSinceLastReport) {
    }

    /** 결과 0건일 때만 채워진다(F3-9 반경 확대 제안). */
    public record Suggestion(String type, int recommendedRadius, long estimatedCount) {
    }
}
