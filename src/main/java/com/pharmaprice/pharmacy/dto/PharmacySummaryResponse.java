package com.pharmaprice.pharmacy.dto;

/** API.md §4 GET /api/v1/pharmacies 목록 항목. distanceM은 lat/lng를 넘겼을 때만 채워진다. */
public record PharmacySummaryResponse(
        Long id,
        String name,
        String addressRoad,
        double lat,
        double lng,
        String phone,
        Integer distanceM,
        PharmacyRegionResponse region
) {
}
