package com.pharmaprice.report.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API.md §6 POST /api/v1/price-reports 요청. */
public record PriceReportRequest(
        @NotNull Long pharmacyId,
        @NotNull Long drugId,
        @NotNull @Min(100) @Max(200000) Integer price,
        // 미입력 시 오늘(KST) — 서비스 레이어에서 채운다.
        LocalDate purchasedAt,
        Long receiptFileId,
        @Size(max = 200) String memo) {
}
