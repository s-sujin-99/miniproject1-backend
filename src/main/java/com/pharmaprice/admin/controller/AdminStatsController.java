package com.pharmaprice.admin.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.admin.dto.DrugStatsResponse;
import com.pharmaprice.admin.dto.OverviewResponse;
import com.pharmaprice.admin.dto.PriceGapsResponse;
import com.pharmaprice.admin.dto.RegionStatsResponse;
import com.pharmaprice.admin.service.AdminStatsService;

import lombok.RequiredArgsConstructor;

/**
 * API.md §8 관리자 통계(ROADMAP T-31). SecurityConfig가 /api/v1/admin/** 전체를 이미
 * hasRole("ADMIN")으로 막지만, 태스크 지침대로 @PreAuthorize를 한 번 더 걸어 컨트롤러 단에서도
 * 명시적으로 드러낸다(shrimp-rules §"인증 방식" — 방어적 이중화).
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private static final int DEFAULT_PRICE_GAP_LIMIT = 10;
    private static final int MAX_PRICE_GAP_LIMIT = 50;

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return adminStatsService.overview();
    }

    @GetMapping("/regions")
    public RegionStatsResponse regions(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Long drugId,
            @RequestParam(required = false) String sido) {
        return adminStatsService.regions(regionCode, drugId, sido);
    }

    @GetMapping("/drugs/{drugId}")
    public DrugStatsResponse drugStats(@PathVariable long drugId) {
        return adminStatsService.drugStats(drugId);
    }

    @GetMapping("/price-gaps")
    public PriceGapsResponse priceGaps(@RequestParam(defaultValue = "" + DEFAULT_PRICE_GAP_LIMIT) int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_PRICE_GAP_LIMIT);
        return adminStatsService.priceGaps(boundedLimit);
    }
}
