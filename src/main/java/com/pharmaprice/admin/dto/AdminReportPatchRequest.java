package com.pharmaprice.admin.dto;

import com.pharmaprice.report.domain.ReportStatus;

/**
 * API.md §8 PATCH /api/v1/admin/price-reports/{reportId} 요청(ROADMAP T-32).
 * 세 필드 모두 선택 — null인 필드는 건드리지 않는다. reason은 자유 텍스트라 DB에 저장할 컬럼이 없어
 * (DATABASE.md §3.5 flag_reason은 고정 enum) 값이 있으면 flagReason을 MANUAL로만 표시한다.
 */
public record AdminReportPatchRequest(ReportStatus status, Boolean flagged, String reason) {
}
