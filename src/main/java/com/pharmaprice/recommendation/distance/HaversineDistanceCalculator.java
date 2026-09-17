package com.pharmaprice.recommendation.distance;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.pharmaprice.recommendation.dto.BoundingBox;

@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    private static final Set<Integer> ALLOWED_RADIUS_M = Set.of(500, 1000, 2000, 5000);

    @Override
    public BoundingBox boundingBox(double lat, double lng, int radiusM) {
        if (!ALLOWED_RADIUS_M.contains(radiusM)) {
            throw new IllegalArgumentException("허용되지 않는 반경입니다: " + radiusM);
        }
        double latDelta = radiusM / METERS_PER_DEGREE_LAT;
        // 경도 1도의 실제 거리는 위도에 따라 cos(lat)만큼 줄어든다.
        // 이 보정을 빼먹으면 한국 위도(37도)에서 반경이 약 25% 과도하게 넓어진다.
        double lngDelta = radiusM / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
        return new BoundingBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }

    @Override
    public double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_M * 2 * Math.asin(Math.sqrt(a));
    }

    /** 대한민국 좌표 범위(위도 33~39, 경도 124~132)를 벗어나는 값을 걸러낸다. */
    public static boolean isValidKoreanCoordinate(double lat, double lng) {
        return lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
    }
}
