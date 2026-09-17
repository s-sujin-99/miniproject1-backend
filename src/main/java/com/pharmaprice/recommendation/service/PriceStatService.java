package com.pharmaprice.recommendation.service;

import java.util.Optional;

import com.pharmaprice.recommendation.dto.PriceStat;

/**
 * (약국, 약품) 조합의 대표가격 통계를 재계산한다. SQL 기반 구현을 다른 계산 방식(예: 배치 집계)으로
 * 교체할 수 있도록 인터페이스 뒤에 둔다(ROADMAP T-10).
 */
public interface PriceStatService {

    /** 해당 조합의 통계를 재계산해 upsert하고, 유효 제보가 0건이면 기존 행을 삭제한다. */
    Optional<PriceStat> recalculate(long pharmacyId, long drugId);
}
