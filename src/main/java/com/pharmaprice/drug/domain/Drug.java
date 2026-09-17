package com.pharmaprice.drug.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일반의약품 마스터(DATABASE.md §3.4). 포장 단위가 다르면 별개 행이라 packageUnit이 필수다.
 * category는 한글 값 목록(해열진통, 소화제 ...)이라 enum이 아닌 문자열로 둔다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "drug")
@EntityListeners(AuditingEntityListener.class)
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_seq", unique = true, length = 20)
    private String itemSeq;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 100)
    private String maker;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 50)
    private String form;

    @Column(name = "package_unit", nullable = false, length = 50)
    private String packageUnit;

    @Column(name = "otc_flag", nullable = false)
    private boolean otcFlag;

    // 시드 생성용 기준가, 운영 시 미사용
    @Column(name = "base_price")
    private Integer basePrice;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Drug(String itemSeq, String name, String displayName, String maker, String category, String form,
            String packageUnit, Integer basePrice, String imageUrl) {
        this.itemSeq = itemSeq;
        this.name = name;
        this.displayName = displayName;
        this.maker = maker;
        this.category = category;
        this.form = form;
        this.packageUnit = packageUnit;
        this.otcFlag = true;
        this.basePrice = basePrice;
        this.imageUrl = imageUrl;
    }
}
