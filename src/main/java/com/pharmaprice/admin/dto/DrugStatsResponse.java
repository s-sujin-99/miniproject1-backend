package com.pharmaprice.admin.dto;

import java.util.List;

/** API.md §8 GET /api/v1/admin/stats/drugs/{drugId} 응답(ROADMAP T-31, F4-4). */
public record DrugStatsResponse(DrugRef drug, List<Bucket> distribution, List<RegionAvg> byRegion, National national) {

    public record DrugRef(long id, String displayName, String packageUnit) {
    }

    public record Bucket(int bucketFrom, int bucketTo, int count) {
    }

    public record RegionAvg(String sido, String sigungu, int avgPrice, int pharmacyCount) {
    }

    public record National(int avg, int median, int min, int max, int stdDev) {
    }
}
