package com.pharmaprice.pharmacy.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약국(DATABASE.md §3.3). updated_at 컬럼이 없어 BaseTimeEntity를 상속하지 않고 created_at만 직접 둔다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "pharmacy")
@EntityListeners(AuditingEntityListener.class)
public class Pharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공공데이터 재적재 시 멱등성 키
    @Column(name = "hira_code", unique = true, length = 30)
    private String hiraCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "address_road")
    private String addressRoad;

    @Column(name = "address_jibun")
    private String addressJibun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code")
    private Region region;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(length = 20)
    private String phone;

    // {"mon":["09:00","19:00"], ..., "holiday":null}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "jsonb")
    private Map<String, List<String>> businessHours;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Pharmacy(String hiraCode, String name, String addressRoad, String addressJibun, Region region,
            double lat, double lng, String phone, Map<String, List<String>> businessHours) {
        this.hiraCode = hiraCode;
        this.name = name;
        this.addressRoad = addressRoad;
        this.addressJibun = addressJibun;
        this.region = region;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.businessHours = businessHours;
        this.active = true;
    }
}
