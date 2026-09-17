package com.pharmaprice.recommendation.dto;

import java.time.LocalDate;

/** SearchQueryRepository가 반경 사각형 안에서 찾은 약국+가격통계 원본 행. */
public record PharmacyPriceCandidate(
        long pharmacyId,
        String pharmacyName,
        String addressRoad,
        double lat,
        double lng,
        String phone,
        int repPrice,
        int minPrice,
        int avgPrice,
        int reportCount,
        LocalDate lastReportedAt
) {
}
