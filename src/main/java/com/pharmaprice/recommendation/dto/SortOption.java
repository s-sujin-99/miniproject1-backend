package com.pharmaprice.recommendation.dto;

/** GET /api/v1/search의 sort 파라미터(API.md §5). Score는 정렬값과 무관하게 항상 계산된다. */
public enum SortOption {
    SCORE, PRICE, DISTANCE
}
