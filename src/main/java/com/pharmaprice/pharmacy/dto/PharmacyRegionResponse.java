package com.pharmaprice.pharmacy.dto;

/** API.md §4 응답의 region 객체. 목록·상세가 공유한다. */
public record PharmacyRegionResponse(String code, String sido, String sigungu) {
}
