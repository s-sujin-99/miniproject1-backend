package com.pharmaprice.pharmacy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.pharmaprice.pharmacy.domain.Region;

public interface RegionRepository extends JpaRepository<Region, String> {

    /**
     * 시도/시군구별 약국 수를 조인 1회로 집계한다(N+1 방지, ROADMAP T-14).
     * 행: [code, sido, sigungu, centerLat, centerLng, pharmacyCount].
     */
    @Query("""
            SELECT r.code, r.sido, r.sigungu, r.centerLat, r.centerLng, COUNT(p)
            FROM Region r LEFT JOIN Pharmacy p ON p.region = r AND p.active = true
            GROUP BY r.code, r.sido, r.sigungu, r.centerLat, r.centerLng
            ORDER BY r.sido ASC, r.sigungu ASC
            """)
    List<Object[]> findAllWithPharmacyCount();
}
