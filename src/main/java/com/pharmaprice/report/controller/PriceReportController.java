package com.pharmaprice.report.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.auth.security.AuthenticatedUser;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.dto.PriceReportListItemResponse;
import com.pharmaprice.report.dto.PriceReportRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.service.PriceReportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API.md §6 가격 제보 생성(ROADMAP T-26). currentUserId는 요청 바디가 아니라 인증 주체에서만 가져온다(shrimp-rules §4.4). */
@RestController
@RequestMapping("/api/v1/price-reports")
@RequiredArgsConstructor
public class PriceReportController {

    private final PriceReportService priceReportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceReportResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PriceReportRequest request) {
        return priceReportService.createReport(user.userId(), request);
    }

    // mine=true는 로그인이 필요하지만 엔드포인트 자체는 공개다(API.md §6) — user는 비로그인 시 null.
    @GetMapping
    public PageResponse<PriceReportListItemResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long pharmacyId,
            @RequestParam(required = false) Long drugId,
            @RequestParam(defaultValue = "false") boolean mine,
            Pageable pageable) {
        Long userId = user == null ? null : user.userId();
        return priceReportService.list(pharmacyId, drugId, mine, userId, pageable);
    }
}
