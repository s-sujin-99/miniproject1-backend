package com.pharmaprice.pharmacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행정구역(DATABASE.md §3.1). 위치 권한 거부 시 폴백 좌표로 쓰는 시드 마스터라 감사 컬럼이 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "region")
public class Region {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 20)
    private String sido;

    @Column(nullable = false, length = 30)
    private String sigungu;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lng", nullable = false)
    private double centerLng;

    @Builder
    private Region(String code, String sido, String sigungu, double centerLat, double centerLng) {
        this.code = code;
        this.sido = sido;
        this.sigungu = sigungu;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
    }
}
