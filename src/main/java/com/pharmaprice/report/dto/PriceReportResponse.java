package com.pharmaprice.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.pharmaprice.recommendation.dto.PriceStat;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;

/** API.md §6 POST /api/v1/price-reports 응답. */
public record PriceReportResponse(
        Long id,
        Long pharmacyId,
        Long drugId,
        int price,
        LocalDate purchasedAt,
        ReportStatus status,
        boolean flagged,
        FlagReason flagReason,
        OffsetDateTime createdAt,
        // 이상치로 판정됐을 때만 채운다(F2-9).
        String warning,
        PriceStat updatedStat) {

    public static PriceReportResponse of(PriceReport report, String warning, PriceStat updatedStat) {
        return new PriceReportResponse(
                report.getId(),
                report.getPharmacy().getId(),
                report.getDrug().getId(),
                report.getPrice(),
                report.getPurchasedAt(),
                report.getStatus(),
                report.isFlagged(),
                report.getFlagReason(),
                report.getCreatedAt(),
                warning,
                updatedStat);
    }
}
