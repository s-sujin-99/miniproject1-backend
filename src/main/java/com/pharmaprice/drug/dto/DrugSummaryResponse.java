package com.pharmaprice.drug.dto;

/** API.md §3 GET /api/v1/drugs 목록 응답 1건. */
public record DrugSummaryResponse(
        Long id,
        String itemSeq,
        String displayName,
        String name,
        String maker,
        String category,
        String form,
        String packageUnit,
        String imageUrl,
        Integer nationalAvgPrice,
        int pharmacyCount
) {
}
