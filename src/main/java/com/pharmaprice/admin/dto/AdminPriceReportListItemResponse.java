package com.pharmaprice.admin.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;

/**
 * API.md §8 GET /api/v1/admin/price-reports 목록 항목(ROADMAP T-32).
 * GET /api/v1/price-reports(T-28)와 동일하되 reporter에 id·email을 더 넣고, flagReason·receiptFileId를
 * 그대로 노출한다 — 관리자는 이상치 판정 근거와 첨부 영수증(GET /api/v1/uploads/{id}, T-27에서 ADMIN 허용)을
 * 직접 확인해야 하므로 일반 목록의 프라이버시 제한(닉네임만, hasReceipt만)을 걷어낸다.
 */
public record AdminPriceReportListItemResponse(
        Long id,
        PharmacyRef pharmacy,
        DrugRef drug,
        int price,
        LocalDate purchasedAt,
        ReporterRef reporter,
        ReportSource source,
        ReportStatus status,
        boolean flagged,
        FlagReason flagReason,
        Long receiptFileId,
        OffsetDateTime createdAt) {

    public static AdminPriceReportListItemResponse from(PriceReport report) {
        return new AdminPriceReportListItemResponse(
                report.getId(),
                new PharmacyRef(report.getPharmacy().getId(), report.getPharmacy().getName()),
                new DrugRef(report.getDrug().getId(), report.getDrug().getDisplayName(), report.getDrug().getPackageUnit()),
                report.getPrice(),
                report.getPurchasedAt(),
                report.getUser() == null ? null
                        : new ReporterRef(report.getUser().getId(), report.getUser().getNickname(), report.getUser().getEmail()),
                report.getSource(),
                report.getStatus(),
                report.isFlagged(),
                report.getFlagReason(),
                report.getReceiptFile() == null ? null : report.getReceiptFile().getId(),
                report.getCreatedAt());
    }

    public record PharmacyRef(Long id, String name) {
    }

    public record DrugRef(Long id, String displayName, String packageUnit) {
    }

    public record ReporterRef(Long id, String nickname, String email) {
    }
}
