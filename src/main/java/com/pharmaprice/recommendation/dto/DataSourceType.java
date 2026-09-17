package com.pharmaprice.recommendation.dto;

/** GET /api/v1/search 응답의 dataSource(API.md §5) — 결과에 포함된 제보 출처. UI 고지 배너 제어용. */
public enum DataSourceType {
    SEED, MIXED, USER
}
