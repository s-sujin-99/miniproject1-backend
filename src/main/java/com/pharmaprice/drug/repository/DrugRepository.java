package com.pharmaprice.drug.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.drug.domain.Drug;

public interface DrugRepository extends JpaRepository<Drug, Long> {
}
