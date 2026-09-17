package com.pharmaprice.recommendation.service;

import java.time.LocalDate;
import java.util.List;

import com.pharmaprice.recommendation.dto.Candidate;
import com.pharmaprice.recommendation.dto.ScoredCandidate;

/**
 * 가격·거리·신선도 가중합산으로 후보를 순위화한다. 후보가 수백 건 수준이라 SQL이 아닌
 * Java 서비스 레이어에서 계산해 단위 테스트를 DB 없이 작성할 수 있게 한다(ROADMAP T-11).
 */
public interface ScoreCalculator {

    List<ScoredCandidate> rank(List<Candidate> candidates, int radiusM, LocalDate today);
}
