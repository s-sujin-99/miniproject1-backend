package com.pharmaprice.drug.dto;

import com.pharmaprice.drug.domain.Drug;

/** API.md §3 GET /api/v1/drugs/{drugId} 응답. */
public record DrugDetailResponse(
        Long id,
        String itemSeq,
        String displayName,
        String name,
        String maker,
        String category,
        String form,
        String packageUnit,
        String imageUrl,
        PriceStats priceStats
) {

    public static DrugDetailResponse from(Drug drug, PriceStats priceStats) {
        return new DrugDetailResponse(
                drug.getId(), drug.getItemSeq(), drug.getDisplayName(), drug.getName(), drug.getMaker(),
                drug.getCategory(), drug.getForm(), drug.getPackageUnit(), drug.getImageUrl(), priceStats);
    }

    /** 가격 통계가 없는 약품(제보 0건)은 각 필드가 null/0으로 채워진다. */
    public record PriceStats(
            Integer nationalAvg,
            Integer nationalMin,
            Integer nationalMax,
            int pharmacyCount,
            int reportCount
    ) {
    }
}
