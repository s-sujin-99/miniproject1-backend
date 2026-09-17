package com.pharmaprice.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.admin.dto.AdminPriceReportListItemResponse;
import com.pharmaprice.admin.dto.AdminReportPatchRequest;
import com.pharmaprice.admin.dto.AdminReportPatchResponse;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.recommendation.dto.PriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * API.md §8 제보 관리(ROADMAP T-32). 상태/플래그 변경 시 통계 재계산은 T-10 PriceStatService.recalculate()를
 * 그대로 재사용한다(재구현 금지 — 태스크 지침).
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final PriceReportRepository priceReportRepository;
    private final PriceStatService priceStatService;

    @Transactional(readOnly = true)
    public PageResponse<AdminPriceReportListItemResponse> list(Long pharmacyId, Long drugId, Boolean flagged,
            ReportStatus status, Pageable pageable) {
        Page<PriceReport> page = priceReportRepository.search(pharmacyId, drugId, null, status, flagged, pageable);
        return PageResponse.of(page.getContent().stream().map(AdminPriceReportListItemResponse::from).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    @Transactional
    public AdminReportPatchResponse updateStatus(long reportId, AdminReportPatchRequest request) {
        PriceReport report = priceReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        ReportStatus previousStatus = report.getStatus();
        if (request.status() != null) {
            report.changeStatus(request.status());
        }
        if (request.flagged() != null) {
            report.changeFlagged(request.flagged());
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            report.markFlagReasonManual();
        }

        // REJECTED로 새로 전환될 때만 1차감 — 이미 REJECTED였던 걸 다시 REJECTED로 보내는 건 변화가 아니다.
        boolean justRejected = request.status() == ReportStatus.REJECTED && previousStatus != ReportStatus.REJECTED;
        if (justRejected && report.getUser() != null) {
            report.getUser().decreaseReportCount();
        }

        // updatedAt은 @LastModifiedDate라 flush 전에는 반영되지 않는다 — 응답에 실제 갱신 시각을 돌려주려면
        // 여기서 강제로 flush해야 한다(T-26 saveAndFlush와 같은 이유).
        priceReportRepository.saveAndFlush(report);

        long pharmacyId = report.getPharmacy().getId();
        long drugId = report.getDrug().getId();
        PriceStat stat = priceStatService.recalculate(pharmacyId, drugId).orElse(null);
        AdminReportPatchResponse.RecalculatedStat recalculatedStat = new AdminReportPatchResponse.RecalculatedStat(
                pharmacyId, drugId, stat == null ? null : stat.repPrice(), stat == null ? 0 : stat.reportCount());

        return new AdminReportPatchResponse(report.getId(), report.getStatus(), report.isFlagged(),
                report.getUpdatedAt(), recalculatedStat);
    }
}
