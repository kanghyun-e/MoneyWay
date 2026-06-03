package com.example.moneyway.ai.service;

import com.example.moneyway.ai.dto.request.TravelPlanRequestDto;
import com.example.moneyway.ai.dto.response.PlanResponseDto;
import com.example.moneyway.place.domain.AiPlaceDto;
import com.example.moneyway.place.dto.internal.NearbyPlaceDto;
import com.example.moneyway.place.repository.PlaceRepository;
import com.example.moneyway.plan.repository.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiPlanServiceTest {

    private static final int BUDGET = 100_000;
    private static final Set<Long> ALLOWED_PLACE_IDS = Set.of(
            1L,
            10L, 11L, 12L, 13L, 14L,
            20L, 21L, 22L, 23L, 24L,
            30L, 31L, 32L, 33L, 34L
    );

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private AiPlanClient aiPlanClient;

    private AiPlanService aiPlanService;

    @BeforeEach
    void setUp() {
        aiPlanService = new AiPlanService(
                planRepository,
                placeRepository,
                aiPlanClient,
                new ObjectMapper()
        );
    }

    @Test
    void rejectsInvalidBudgetBeforeCallingExternalDependencies() {
        TravelPlanRequestDto request = request(0, 1);

        assertThatThrownBy(() -> aiPlanService.generatePlanWithAI(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예산");

        verifyNoInteractions(placeRepository, aiPlanClient);
    }

    @Test
    void fillsMissingRequiredSlots() throws Exception {
        givenDefaultCandidates();
        given(aiPlanClient.requestPlan(anyString())).willReturn("""
                {
                  "totalUsedCost": 0,
                  "duration": 1,
                  "days": [
                    {
                      "day": "1일차",
                      "places": [
                        {
                          "placeId": 0,
                          "title": "오전 자유시간",
                          "address": null,
                          "categoryName": "TOURIST_ATTRACTION",
                          "priceInfo": "0",
                          "latitude": null,
                          "longitude": null,
                          "time": "오전",
                          "cost": 0,
                          "startTime": "09:00",
                          "endTime": "11:00"
                        }
                      ],
                      "totalBudget": 100000,
                      "usedCost": 0
                    }
                  ]
                }
                """);

        PlanResponseDto result = aiPlanService.generatePlanWithAI(request(BUDGET, 1));

        assertThat(result.days()).hasSize(1);
        assertThat(result.days().get(0).places())
                .extracting(AiPlaceDto::time)
                .containsExactly("오전", "점심", "카페", "오후", "저녁");
        assertThat(result.days().get(0).places())
                .allSatisfy(place -> {
                    assertThat(place.startTime()).isNotBlank();
                    assertThat(place.endTime()).isNotBlank();
                });
    }

    @Test
    void replacesWrongMealCategoriesWithRestaurants() throws Exception {
        givenDefaultCandidates();
        given(aiPlanClient.requestPlan(anyString())).willReturn(responseWithWrongMealCategories());

        PlanResponseDto result = aiPlanService.generatePlanWithAI(request(BUDGET, 1));

        assertThat(findBySlot(result, "점심").categoryName()).isEqualTo("RESTAURANT");
        assertThat(findBySlot(result, "저녁").categoryName()).isEqualTo("RESTAURANT");
    }

    @Test
    void removesPlaceIdsOutsideDatabaseCandidateSet() throws Exception {
        givenDefaultCandidates();
        given(aiPlanClient.requestPlan(anyString())).willReturn(responseWithUnknownPlaceIds());

        PlanResponseDto result = aiPlanService.generatePlanWithAI(request(BUDGET, 1));

        assertThat(result.days().get(0).places())
                .noneMatch(place -> Long.valueOf(999L).equals(place.placeId()))
                .allSatisfy(place -> {
                    if (place.placeId() != 0L) {
                        assertThat(ALLOWED_PLACE_IDS).contains(place.placeId());
                    }
                });
    }

    @Test
    void capsTotalUsedCostToRequestedBudget() throws Exception {
        givenDefaultCandidates();
        given(aiPlanClient.requestPlan(anyString())).willReturn(responseOverBudget());

        PlanResponseDto result = aiPlanService.generatePlanWithAI(request(BUDGET, 1));

        assertThat(result.totalUsedCost()).isLessThanOrEqualTo(BUDGET);
    }

    @Test
    void keepsReliabilityRulesAcrossThirtyAiResponseVariants() throws Exception {
        givenDefaultCandidates();
        AtomicInteger sequence = new AtomicInteger();
        given(aiPlanClient.requestPlan(anyString()))
                .willAnswer(invocation -> reliabilityResponseVariant(sequence.getAndIncrement()));

        for (int i = 0; i < 30; i++) {
            PlanResponseDto result = aiPlanService.generatePlanWithAI(request(BUDGET, 1));

            assertThat(result.totalUsedCost()).isLessThanOrEqualTo(BUDGET);
            assertThat(result.days().get(0).places())
                    .extracting(AiPlaceDto::time)
                    .containsExactly("오전", "점심", "카페", "오후", "저녁");
            assertThat(result.days().get(0).places())
                    .noneMatch(place -> Long.valueOf(999L).equals(place.placeId()))
                    .allSatisfy(this::assertSlotCategory);
        }
    }

    private void givenDefaultCandidates() {
        given(placeRepository.findTopAccommodations(anyInt()))
                .willReturn(List.of(nearby(1L, "제주 숙소", "ACCOMMODATION", "40000", 33.45, 126.55)));
        given(placeRepository.findTourAndActivityNearby(anyInt(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(
                        nearby(10L, "성산일출봉", "TOURIST_ATTRACTION", "10000", 33.46, 126.93),
                        nearby(11L, "우도", "TOURIST_ATTRACTION", "8000", 33.50, 126.95),
                        nearby(12L, "오름 산책", "ACTIVITY", "5000", 33.41, 126.70),
                        nearby(13L, "해변 산책", "TOURIST_ATTRACTION", "0", 33.40, 126.60),
                        nearby(14L, "숲길 체험", "ACTIVITY", "7000", 33.38, 126.52)
                ));
        given(placeRepository.findRestaurantsNearby(anyInt(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(
                        nearby(20L, "점심 식당", "RESTAURANT", "12000", 33.45, 126.54),
                        nearby(21L, "저녁 식당", "RESTAURANT", "15000", 33.46, 126.56),
                        nearby(22L, "해물 식당", "RESTAURANT", "13000", 33.47, 126.57),
                        nearby(23L, "국수 식당", "RESTAURANT", "9000", 33.48, 126.58),
                        nearby(24L, "분식 식당", "RESTAURANT", "8000", 33.49, 126.59)
                ));
        given(placeRepository.findCafesNearby(anyInt(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(
                        nearby(30L, "바다 카페", "CAFE", "7000", 33.45, 126.53),
                        nearby(31L, "숲 카페", "CAFE", "6500", 33.44, 126.52),
                        nearby(32L, "돌담 카페", "CAFE", "6000", 33.43, 126.51),
                        nearby(33L, "노을 카페", "CAFE", "8000", 33.42, 126.50),
                        nearby(34L, "정원 카페", "CAFE", "7500", 33.41, 126.49)
                ));
    }

    private TravelPlanRequestDto request(int budget, int duration) {
        TravelPlanRequestDto request = new TravelPlanRequestDto();
        request.setBudget(budget);
        request.setDuration(duration);
        request.setThemes(List.of("바다"));
        return request;
    }

    private AiPlaceDto findBySlot(PlanResponseDto result, String slot) {
        return result.days().get(0).places().stream()
                .filter(place -> slot.equals(place.time()))
                .findFirst()
                .orElseThrow();
    }

    private void assertSlotCategory(AiPlaceDto place) {
        switch (place.time()) {
            case "오전", "오후" -> assertThat(place.categoryName()).isIn("TOURIST_ATTRACTION", "ACTIVITY");
            case "점심", "저녁" -> assertThat(place.categoryName()).isEqualTo("RESTAURANT");
            case "카페" -> assertThat(place.categoryName()).isEqualTo("CAFE");
            case "숙소" -> assertThat(place.categoryName()).isEqualTo("ACCOMMODATION");
            default -> throw new AssertionError("Unexpected slot: " + place.time());
        }
    }

    private String reliabilityResponseVariant(int index) {
        return switch (index % 3) {
            case 0 -> responseWithUnknownPlaceIds();
            case 1 -> responseWithWrongMealCategories();
            default -> responseOverBudget();
        };
    }

    private String responseWithUnknownPlaceIds() {
        return """
                {
                  "totalUsedCost": 50000,
                  "duration": 1,
                  "days": [
                    {
                      "day": "1일차",
                      "places": [
                        {"placeId": 999, "title": "없는 관광지", "address": null, "categoryName": "TOURIST_ATTRACTION", "priceInfo": "10000", "latitude": "33.0", "longitude": "126.0", "time": "오전", "cost": 10000, "startTime": "09:00", "endTime": "11:00"},
                        {"placeId": 999, "title": "없는 식당", "address": null, "categoryName": "RESTAURANT", "priceInfo": "10000", "latitude": "33.0", "longitude": "126.0", "time": "점심", "cost": 10000, "startTime": "11:30", "endTime": "12:30"},
                        {"placeId": 999, "title": "없는 카페", "address": null, "categoryName": "CAFE", "priceInfo": "5000", "latitude": "33.0", "longitude": "126.0", "time": "카페", "cost": 5000, "startTime": "14:00", "endTime": "15:00"}
                      ],
                      "totalBudget": 100000,
                      "usedCost": 25000
                    }
                  ]
                }
                """;
    }

    private String responseWithWrongMealCategories() {
        return """
                {
                  "totalUsedCost": 30000,
                  "duration": 1,
                  "days": [
                    {
                      "day": "1일차",
                      "places": [
                        {"placeId": 10, "title": "성산일출봉", "address": null, "categoryName": "TOURIST_ATTRACTION", "priceInfo": "10000", "latitude": "33.46", "longitude": "126.93", "time": "오전", "cost": 10000, "startTime": "09:00", "endTime": "11:00"},
                        {"placeId": 11, "title": "우도", "address": null, "categoryName": "TOURIST_ATTRACTION", "priceInfo": "8000", "latitude": "33.50", "longitude": "126.95", "time": "점심", "cost": 8000, "startTime": "11:30", "endTime": "12:30"},
                        {"placeId": 20, "title": "점심 식당", "address": null, "categoryName": "RESTAURANT", "priceInfo": "12000", "latitude": "33.45", "longitude": "126.54", "time": "카페", "cost": 12000, "startTime": "14:00", "endTime": "15:00"},
                        {"placeId": 12, "title": "오름 산책", "address": null, "categoryName": "ACTIVITY", "priceInfo": "5000", "latitude": "33.41", "longitude": "126.70", "time": "저녁", "cost": 5000, "startTime": "18:30", "endTime": "20:00"}
                      ],
                      "totalBudget": 100000,
                      "usedCost": 35000
                    }
                  ]
                }
                """;
    }

    private String responseOverBudget() {
        return """
                {
                  "totalUsedCost": 450000,
                  "duration": 1,
                  "days": [
                    {
                      "day": "1일차",
                      "places": [
                        {"placeId": 10, "title": "성산일출봉", "address": null, "categoryName": "TOURIST_ATTRACTION", "priceInfo": "10000", "latitude": "33.46", "longitude": "126.93", "time": "오전", "cost": 90000, "startTime": "09:00", "endTime": "11:00"},
                        {"placeId": 20, "title": "점심 식당", "address": null, "categoryName": "RESTAURANT", "priceInfo": "12000", "latitude": "33.45", "longitude": "126.54", "time": "점심", "cost": 90000, "startTime": "11:30", "endTime": "12:30"},
                        {"placeId": 30, "title": "바다 카페", "address": null, "categoryName": "CAFE", "priceInfo": "7000", "latitude": "33.45", "longitude": "126.53", "time": "카페", "cost": 90000, "startTime": "14:00", "endTime": "15:00"},
                        {"placeId": 11, "title": "우도", "address": null, "categoryName": "TOURIST_ATTRACTION", "priceInfo": "8000", "latitude": "33.50", "longitude": "126.95", "time": "오후", "cost": 90000, "startTime": "15:30", "endTime": "18:00"},
                        {"placeId": 21, "title": "저녁 식당", "address": null, "categoryName": "RESTAURANT", "priceInfo": "15000", "latitude": "33.46", "longitude": "126.56", "time": "저녁", "cost": 90000, "startTime": "18:30", "endTime": "20:00"}
                      ],
                      "totalBudget": 100000,
                      "usedCost": 450000
                    }
                  ]
                }
                """;
    }

    private NearbyPlaceDto nearby(Long id, String title, String categoryName, String priceInfo, Double lat, Double lng) {
        return new TestNearbyPlace(id, title, categoryName, priceInfo, lat, lng);
    }

    private record TestNearbyPlace(
            Long id,
            String title,
            String categoryName,
            String priceInfo,
            Double lat,
            Double lng
    ) implements NearbyPlaceDto {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getCategoryName() {
            return categoryName;
        }

        @Override
        public String getAddress() {
            return "제주";
        }

        @Override
        public String getThumbnailUrl() {
            return null;
        }

        @Override
        public String getThumbnailUrl2() {
            return null;
        }

        @Override
        public String getPriceInfo() {
            return priceInfo;
        }

        @Override
        public Double getMapx() {
            return lng;
        }

        @Override
        public Double getMapy() {
            return lat;
        }

        @Override
        public Double getDistance() {
            return 1.0;
        }
    }
}
