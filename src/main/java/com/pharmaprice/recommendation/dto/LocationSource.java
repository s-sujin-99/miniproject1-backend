package com.pharmaprice.recommendation.dto;

/** GET /api/v1/search 응답의 query.locationSource(API.md §5) — 사용자 좌표 출처. */
public enum LocationSource {
    GPS, REGION
}
