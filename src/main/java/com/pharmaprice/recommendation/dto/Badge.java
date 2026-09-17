package com.pharmaprice.recommendation.dto;

/** 검색 결과 카드에 붙는 순위 근거 뱃지(API.md §5). */
public enum Badge {
    LOWEST_PRICE,
    LOW_CONFIDENCE,
    STALE_DATA,
    NEAREST
}
