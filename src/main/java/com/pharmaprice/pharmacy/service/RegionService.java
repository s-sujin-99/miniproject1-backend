package com.pharmaprice.pharmacy.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.dto.RegionGroupResponse.SigunguResponse;
import com.pharmaprice.pharmacy.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;

    public List<RegionGroupResponse> listGroupedBySido() {
        // 쿼리가 이미 sido ASC로 정렬돼 있어 LinkedHashMap 삽입 순서만으로 그룹 순서가 유지된다.
        Map<String, List<SigunguResponse>> grouped = new LinkedHashMap<>();
        for (Object[] row : regionRepository.findAllWithPharmacyCount()) {
            SigunguResponse sigungu = new SigunguResponse(
                    (String) row[0], (String) row[2], (double) row[3], (double) row[4], ((Number) row[5]).longValue());
            grouped.computeIfAbsent((String) row[1], k -> new ArrayList<>()).add(sigungu);
        }
        return grouped.entrySet().stream()
                .map(e -> new RegionGroupResponse(e.getKey(), e.getValue()))
                .toList();
    }
}
