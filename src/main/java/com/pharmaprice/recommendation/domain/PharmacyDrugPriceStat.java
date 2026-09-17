package com.pharmaprice.recommendation.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약국·약품 쌍별 가격 통계 캐시(DATABASE.md §3.6). 검색 경로가 읽는 유일한 집계 테이블.
 * calculatedAt은 재계산 시점을 서비스가 넣는 값이라 Auditing을 쓰지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "pharmacy_drug_price_stat",
        uniqueConstraints = @UniqueConstraint(name = "uq_stat_pair", columnNames = {"pharmacy_id", "drug_id"}))
public class PharmacyDrugPriceStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    // 대표가격 = 유효 제보의 중앙값
    @Column(name = "rep_price", nullable = false)
    private int repPrice;

    @Column(name = "min_price", nullable = false)
    private int minPrice;

    @Column(name = "max_price", nullable = false)
    private int maxPrice;

    @Column(name = "avg_price", nullable = false)
    private int avgPrice;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "last_reported_at", nullable = false)
    private LocalDate lastReportedAt;

    // 계산에 쓴 창 크기(90 또는 180)
    @Column(name = "window_days", nullable = false)
    private short windowDays;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @Builder
    private PharmacyDrugPriceStat(Pharmacy pharmacy, Drug drug, int repPrice, int minPrice, int maxPrice,
            int avgPrice, int reportCount, LocalDate lastReportedAt, short windowDays, OffsetDateTime calculatedAt) {
        this.pharmacy = pharmacy;
        this.drug = drug;
        this.repPrice = repPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.avgPrice = avgPrice;
        this.reportCount = reportCount;
        this.lastReportedAt = lastReportedAt;
        this.windowDays = windowDays;
        this.calculatedAt = calculatedAt;
    }

    /** 재계산 결과로 통계를 갱신한다. 세터 대신 의미 있는 메서드로 상태를 바꾼다(shrimp-rules §3.2). */
    public void applyStats(int repPrice, int minPrice, int maxPrice, int avgPrice, int reportCount,
            LocalDate lastReportedAt, short windowDays, OffsetDateTime calculatedAt) {
        this.repPrice = repPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.avgPrice = avgPrice;
        this.reportCount = reportCount;
        this.lastReportedAt = lastReportedAt;
        this.windowDays = windowDays;
        this.calculatedAt = calculatedAt;
    }
}
