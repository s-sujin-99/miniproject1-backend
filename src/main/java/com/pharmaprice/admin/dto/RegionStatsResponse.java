package com.pharmaprice.admin.dto;

import java.util.List;

/** API.md §8 GET /api/v1/admin/stats/regions 응답(ROADMAP T-31, F4-3). */
public record RegionStatsResponse(List<Row> rows) {

    public record Row(RegionRef region, DrugRef drug, int avgPrice, int minPrice, int maxPrice,
                       int pharmacyCount, int reportCount) {
    }

    public record RegionRef(String code, String sido, String sigungu) {
    }

    public record DrugRef(long id, String displayName) {
    }
}
