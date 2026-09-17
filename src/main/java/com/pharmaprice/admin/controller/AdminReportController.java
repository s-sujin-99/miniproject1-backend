package com.pharmaprice.admin.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.admin.dto.AdminPriceReportListItemResponse;
import com.pharmaprice.admin.dto.AdminReportPatchRequest;
import com.pharmaprice.admin.dto.AdminReportPatchResponse;
import com.pharmaprice.admin.service.AdminReportService;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.domain.ReportStatus;

import lombok.RequiredArgsConstructor;

/**
 * API.md §8 제보 관리(ROADMAP T-32). SecurityConfig가 /api/v1/admin/** 전체를 이미 hasRole("ADMIN")으로
 * 막지만, T-31과 같은 이유로 @PreAuthorize를 한 번 더 명시적으로 건다.
 */
@RestController
@RequestMapping("/api/v1/admin/price-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    public PageResponse<AdminPriceReportListItemResponse> list(
            @RequestParam(required = false) Boolean flagged,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Long pharmacyId,
            @RequestParam(required = false) Long drugId,
            Pageable pageable) {
        return adminReportService.list(pharmacyId, drugId, flagged, status, pageable);
    }

    @PatchMapping("/{reportId}")
    public AdminReportPatchResponse updateStatus(@PathVariable long reportId,
            @RequestBody AdminReportPatchRequest request) {
        return adminReportService.updateStatus(reportId, request);
    }
}
