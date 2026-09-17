package com.pharmaprice.pharmacy.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.service.PharmacyService;

import lombok.RequiredArgsConstructor;

/** API.md §4 약국 검색/상세 — 제보 폼의 약국 선택 및 약국 상세 화면용. */
@RestController
@RequestMapping("/api/v1/pharmacies")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping
    public PageResponse<PharmacySummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "2000") int radius,
            Pageable pageable) {
        return pharmacyService.search(q, lat, lng, radius, pageable);
    }

    @GetMapping("/{pharmacyId}")
    public PharmacyDetailResponse getDetail(
            @PathVariable long pharmacyId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return pharmacyService.getDetail(pharmacyId, lat, lng);
    }

    @GetMapping("/{pharmacyId}/drugs/{drugId}/history")
    public PriceHistoryResponse getPriceHistory(
            @PathVariable long pharmacyId,
            @PathVariable long drugId,
            @RequestParam(defaultValue = "180") int days) {
        return pharmacyService.getPriceHistory(pharmacyId, drugId, days);
    }
}
