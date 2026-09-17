package com.pharmaprice.drug.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.service.DrugService;

import lombok.RequiredArgsConstructor;

/** API.md §3 의약품 검색/상세. */
@RestController
@RequestMapping("/api/v1/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    @GetMapping
    public PageResponse<DrugSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        return drugService.search(q, category, pageable);
    }

    @GetMapping("/{drugId}")
    public DrugDetailResponse getDetail(@PathVariable long drugId) {
        return drugService.getDetail(drugId);
    }
}
