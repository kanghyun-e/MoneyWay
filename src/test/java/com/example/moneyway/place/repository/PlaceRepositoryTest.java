package com.example.moneyway.place.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceRepositoryTest {

    @Test
    void nearbyPlaceQueriesApplyRadiusFiltering() throws Exception {
        assertNearbyQueryUsesRadius("findTourAndActivityNearby");
        assertNearbyQueryUsesRadius("findRestaurantsNearby");
        assertNearbyQueryUsesRadius("findCafesNearby");
    }

    private void assertNearbyQueryUsesRadius(String methodName) throws NoSuchMethodException {
        Method method = PlaceRepository.class.getMethod(
                methodName,
                int.class,
                double.class,
                double.class,
                double.class
        );

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("6371 * ACOS")
                .contains("HAVING distance <= :radius")
                .contains("LIMIT 20");
    }
}
