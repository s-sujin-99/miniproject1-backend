package com.pharmaprice.recommendation.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.pharmaprice.recommendation.dto.BoundingBox;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void 강남역_역삼역_거리가_실측값_오차_1퍼센트_이내다() {
        double distance = calculator.distanceMeters(37.4979, 127.0276, 37.5006, 127.0366);
        assertThat(distance).isCloseTo(850, org.assertj.core.data.Percentage.withPercentage(1));
    }

    @Test
    void 바운딩_박스가_반경_원의_4방향_경계를_모두_포함한다() {
        double lat = 37.5;
        double lng = 127.0;
        int radiusM = 2000;
        BoundingBox box = calculator.boundingBox(lat, lng, radiusM);

        // 북/남/동/서 경계 좌표(방위각 0/180/90/270)를 직접 계산해 박스 안에 들어오는지 확인한다.
        double north = lat + radiusM / 111_320.0;
        double south = lat - radiusM / 111_320.0;
        double east = lng + radiusM / (111_320.0 * Math.cos(Math.toRadians(lat)));
        double west = lng - radiusM / (111_320.0 * Math.cos(Math.toRadians(lat)));

        assertThat(north).isLessThanOrEqualTo(box.maxLat());
        assertThat(south).isGreaterThanOrEqualTo(box.minLat());
        assertThat(east).isLessThanOrEqualTo(box.maxLng());
        assertThat(west).isGreaterThanOrEqualTo(box.minLng());
    }

    @Test
    void 위도가_다르면_lngDelta도_다르게_계산된다() {
        BoundingBox low = calculator.boundingBox(33, 127, 2000);
        BoundingBox high = calculator.boundingBox(38, 127, 2000);

        double lowLngDelta = low.maxLng() - low.minLng();
        double highLngDelta = high.maxLng() - high.minLng();

        assertThat(lowLngDelta).isNotEqualTo(highLngDelta);
        // 위도가 높을수록 cos(lat)가 작아지므로 같은 거리를 표현하는 경도 폭은 더 넓어야 한다.
        assertThat(highLngDelta).isGreaterThan(lowLngDelta);
    }

    @Test
    void 허용되지_않는_반경은_예외를_던진다() {
        for (int invalidRadius : new int[] {100, 1500, 3000, 10000}) {
            assertThatThrownBy(() -> calculator.boundingBox(37.5, 127.0, invalidRadius))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 대한민국_범위_밖_좌표는_유효하지_않다() {
        assertThat(HaversineDistanceCalculator.isValidKoreanCoordinate(37.5, 127.0)).isTrue();
        assertThat(HaversineDistanceCalculator.isValidKoreanCoordinate(10.0, 127.0)).isFalse();
        assertThat(HaversineDistanceCalculator.isValidKoreanCoordinate(37.5, 200.0)).isFalse();
    }
}
