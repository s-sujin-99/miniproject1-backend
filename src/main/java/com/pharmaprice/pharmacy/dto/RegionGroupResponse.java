package com.pharmaprice.pharmacy.dto;

import java.util.List;

/** API.md §7 GET /api/v1/regions 응답 — 시도별 그룹. */
public record RegionGroupResponse(String sido, List<SigunguResponse> sigungus) {

    public record SigunguResponse(String code, String sigungu, double centerLat, double centerLng, long pharmacyCount) {
    }
}
