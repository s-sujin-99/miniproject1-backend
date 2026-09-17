package com.pharmaprice.recommendation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;

public interface PharmacyDrugPriceStatRepository extends JpaRepository<PharmacyDrugPriceStat, Long> {

    Optional<PharmacyDrugPriceStat> findByPharmacyIdAndDrugId(Long pharmacyId, Long drugId);
}
