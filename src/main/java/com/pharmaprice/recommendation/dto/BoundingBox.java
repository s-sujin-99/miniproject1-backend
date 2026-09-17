package com.pharmaprice.recommendation.dto;

/**
 * 반경 검색의 1차 사각형 필터(DATABASE.md §4.1). 모서리에 반경 밖 좌표가 섞이므로
 * 반드시 DistanceCalculator.distanceMeters로 2차 필터링해야 한다.
 */
public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
}
