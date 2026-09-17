package com.pharmaprice.admin.dto;

import java.time.OffsetDateTime;

import com.pharmaprice.report.domain.ReportStatus;

/** API.md §8 PATCH /api/v1/admin/price-reports/{reportId} 응답(ROADMAP T-32). */
public record AdminReportPatchResponse(
        Long id,
        ReportStatus status,
        boolean flagged,
        OffsetDateTime updatedAt,
        RecalculatedStat recalculatedStat) {

    /** recalculate()가 Optional.empty()면(유효 제보 0건) repPrice는 null, reportCount는 0으로 내려준다. */
    public record RecalculatedStat(long pharmacyId, long drugId, Integer repPrice, int reportCount) {
    }
}
