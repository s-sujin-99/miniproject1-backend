package com.pharmaprice.admin.dto;

import java.util.List;

/** API.md §8 GET /api/v1/admin/stats/price-gaps 응답(ROADMAP T-31, DATABASE.md §5.4). */
public record PriceGapsResponse(List<Row> rows) {

    public record Row(DrugRef drug, RegionRef cheapestRegion, RegionRef priciestRegion, int gap, double gapPct) {
    }

    public record DrugRef(long id, String displayName) {
    }

    public record RegionRef(String sido, String sigungu, int avgPrice) {
    }
}
