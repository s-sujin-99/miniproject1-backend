package com.pharmaprice.drug.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.repository.DrugQueryRepository;
import com.pharmaprice.drug.repository.DrugRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrugService {

    private final DrugRepository drugRepository;
    private final DrugQueryRepository drugQueryRepository;

    public PageResponse<DrugSummaryResponse> search(String q, String category, Pageable pageable) {
        List<DrugSummaryResponse> content = drugQueryRepository.search(q, category, pageable);
        long totalElements = drugQueryRepository.count(q, category);
        return PageResponse.of(content, pageable.getPageNumber(), pageable.getPageSize(), totalElements);
    }

    public DrugDetailResponse getDetail(long drugId) {
        Drug drug = drugRepository.findById(drugId)
                .filter(Drug::isOtcFlag) // 전문의약품은 어떤 응답에도 포함 금지(shrimp-rules §4.6)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRUG_NOT_FOUND));
        DrugDetailResponse.PriceStats priceStats = drugQueryRepository.findStats(drugId);
        return DrugDetailResponse.from(drug, priceStats);
    }
}
