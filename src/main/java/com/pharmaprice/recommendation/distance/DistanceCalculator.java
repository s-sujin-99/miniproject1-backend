package com.pharmaprice.recommendation.distance;

import com.pharmaprice.recommendation.dto.BoundingBox;

/**
 * PostGIS 없이 반경 검색을 수행한다. 나중에 ST_DWithin 기반 구현체로 교체할 수 있도록
 * 인터페이스 뒤에 둔다(DATABASE.md §4.2).
 */
public interface DistanceCalculator {

    BoundingBox boundingBox(double lat, double lng, int radiusM);

    double distanceMeters(double lat1, double lng1, double lat2, double lng2);
}
