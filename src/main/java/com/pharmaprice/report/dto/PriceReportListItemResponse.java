package com.pharmaprice.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;

/**
 * API.md §6 GET /api/v1/price-reports 목록 항목. reporter는 닉네임만 노출한다(id·이메일 금지).
 * hasReceipt는 boolean만 내려준다 — 파일 id를 일반 사용자에게 주면 /api/v1/uploads/{fileId}로 바로
 * 접근 시도가 가능해지므로 노출하지 않는다.
 */
public record PriceReportListItemResponse(
        Long id,
        PharmacyRef pharmacy,
        DrugRef drug,
        int price,
        LocalDate purchasedAt,
        ReporterRef reporter,
        ReportSource source,
        ReportStatus status,
        boolean flagged,
        boolean hasReceipt,
        OffsetDateTime createdAt) {

    public static PriceReportListItemResponse from(PriceReport report) {
        return new PriceReportListItemResponse(
                report.getId(),
                new PharmacyRef(report.getPharmacy().getId(), report.getPharmacy().getName()),
                new DrugRef(report.getDrug().getId(), report.getDrug().getDisplayName(), report.getDrug().getPackageUnit()),
                report.getPrice(),
                report.getPurchasedAt(),
                report.getUser() == null ? null : new ReporterRef(report.getUser().getNickname()),
                report.getSource(),
                report.getStatus(),
                report.isFlagged(),
                report.getReceiptFile() != null,
                report.getCreatedAt());
    }

    public record PharmacyRef(Long id, String name) {
    }

    public record DrugRef(Long id, String displayName, String packageUnit) {
    }

    public record ReporterRef(String nickname) {
    }
}
