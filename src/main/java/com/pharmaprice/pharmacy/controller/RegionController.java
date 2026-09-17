package com.pharmaprice.pharmacy.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.service.RegionService;

import lombok.RequiredArgsConstructor;

/** API.md §7 지역 목록 — 위치 권한 거부 시 폴백 드롭다운용. 전체 ~250건이라 페이지네이션이 없다. */
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public ResponseEntity<List<RegionGroupResponse>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(regionService.listGroupedBySido());
    }
}
