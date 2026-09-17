package com.pharmaprice.admin.dto;

import java.time.LocalDate;
import java.util.List;

/** API.md §8 GET /api/v1/admin/stats/overview 응답(ROADMAP T-31). */
public record OverviewResponse(Totals totals, List<TrendPoint> recentTrend, long flaggedReportCount, double coverageRate) {

    public record Totals(int pharmacyCount, int drugCount, int reportCount, int userCount, int coveredPairCount) {
    }

    public record TrendPoint(LocalDate date, int reportCount) {
    }
}
