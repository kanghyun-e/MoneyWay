package com.example.moneyway.place.service;

import com.example.moneyway.infrastructure.external.tourapi.TourApiClient;
import com.example.moneyway.place.domain.RestaurantJeju;
import com.example.moneyway.place.dto.response.ExcelUploadResult;
import com.example.moneyway.place.repository.PlaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDataServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private TourUpdateHelper tourUpdateHelper;

    @Mock
    private ExecutorService dataSyncTaskExecutor;

    private AdminDataService adminDataService;

    @BeforeEach
    void setUp() {
        adminDataService = new AdminDataService(
                placeRepository,
                tourApiClient,
                new ObjectMapper(),
                tourUpdateHelper,
                dataSyncTaskExecutor
        );
    }

    @Test
    void loadRestaurantsFromExcelDeduplicatesNewRowsAndUpdatesExistingRows() throws Exception {
        RestaurantJeju existingRestaurant = RestaurantJeju.builder()
                .title("기존식당")
                .address("제주시")
                .rating(3.0)
                .topReview("기존 리뷰")
                .build();

        given(placeRepository.findExistingRestaurantUniqueKeys(anyList()))
                .willReturn(Set.of("기존식당||제주시"));
        given(placeRepository.findByTitleAndAddress("기존식당", "제주시"))
                .willReturn(Optional.of(existingRestaurant));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "restaurants.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                restaurantWorkbookBytes()
        );

        ExcelUploadResult result = adminDataService.loadRestaurantsFromExcel(file);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(existingRestaurant.getRating()).isEqualTo(4.8);
        assertThat(existingRestaurant.getTopReview()).isEqualTo("업데이트 리뷰");

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<RestaurantJeju>> savedCaptor = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(placeRepository).saveAll(savedCaptor.capture());

        List<RestaurantJeju> savedRestaurants = StreamSupport.stream(savedCaptor.getValue().spliterator(), false)
                .toList();
        assertThat(savedRestaurants)
                .extracting(RestaurantJeju::getTitle)
                .containsExactly("새식당");
    }

    private byte[] restaurantWorkbookBytes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("restaurants");
            createRow(sheet, 0,
                    "title",
                    "address",
                    "mapx",
                    "mapy",
                    "rating",
                    "top_review",
                    "price2",
                    "category_code",
                    "score",
                    "review_count",
                    "tel",
                    "menu",
                    "url",
                    "img"
            );
            createRow(sheet, 1,
                    "기존식당",
                    "제주시",
                    "126.55",
                    "33.45",
                    "4.8",
                    "업데이트 리뷰",
                    "12000",
                    "c1",
                    "4.8",
                    "120",
                    "064-000-0000",
                    "고기국수",
                    "https://example.com/old",
                    "https://example.com/old.jpg"
            );
            createRow(sheet, 2,
                    "기존식당",
                    "제주시",
                    "126.55",
                    "33.45",
                    "4.8",
                    "업데이트 리뷰",
                    "12000",
                    "c1",
                    "4.8",
                    "120",
                    "064-000-0000",
                    "고기국수",
                    "https://example.com/old",
                    "https://example.com/old.jpg"
            );
            createRow(sheet, 3,
                    "새식당",
                    "서귀포시",
                    "126.60",
                    "33.25",
                    "4.5",
                    "신규 리뷰",
                    "15000",
                    "c1",
                    "4.5",
                    "80",
                    "064-111-1111",
                    "갈치조림",
                    "https://example.com/new",
                    "https://example.com/new.jpg"
            );

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void createRow(Sheet sheet, int rowNumber, String... values) {
        Row row = sheet.createRow(rowNumber);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
