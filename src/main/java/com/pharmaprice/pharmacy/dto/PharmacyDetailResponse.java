package com.pharmaprice.pharmacy.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** API.md §4 GET /api/v1/pharmacies/{pharmacyId} 응답. */
public record PharmacyDetailResponse(
        Long id,
        String name,
        String addressRoad,
        String addressJibun,
        double lat,
        double lng,
        String phone,
        Map<String, List<String>> businessHours,
        Integer distanceM,
        PharmacyRegionResponse region,
        List<DrugPriceResponse> drugPrices
) {

    /** repPrice 오름차순으로 정렬해 내려준다(API.md §4). */
    public record DrugPriceResponse(
            Long drugId,
            String displayName,
            String packageUnit,
            int repPrice,
            int minPrice,
            int maxPrice,
            int avgPrice,
            int reportCount,
            LocalDate lastReportedAt,
            Integer nationalAvgPrice,
            Integer diffFromNationalAvg
    ) {
    }
}
