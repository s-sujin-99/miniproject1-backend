package com.pharmaprice.pharmacy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.pharmacy.domain.Pharmacy;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
}
