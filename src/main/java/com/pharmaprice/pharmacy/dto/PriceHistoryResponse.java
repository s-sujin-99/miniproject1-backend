package com.pharmaprice.pharmacy.dto;

import java.time.LocalDate;
import java.util.List;

/** API.md §4 GET /api/v1/pharmacies/{pharmacyId}/drugs/{drugId}/history 응답. */
public record PriceHistoryResponse(Long pharmacyId, Long drugId, List<PricePoint> points) {

    /** flagged:true인 점도 그대로 포함한다 — 통계에서 빠진 이상치를 프론트가 회색 점선으로 표시하는 유일한 지점(ROADMAP T-20). */
    public record PricePoint(LocalDate purchasedAt, int price, boolean flagged) {
    }
}
