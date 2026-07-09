package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.service.CustomerManagementService;
import com.fitback.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerManagementControllerTest {

    @Test
    @DisplayName("상단 통계 조회 API는 서비스 결과를 ApiResponse로 반환한다")
    void getSummary() {
        CustomerManagementService service = mock(CustomerManagementService.class);
        CustomerManagementController controller = new CustomerManagementController(service);
        UUID storeId = UUID.randomUUID();
        CustomerManagementSummaryResponse serviceResponse = CustomerManagementSummaryResponse.builder()
                .month("2026-10")
                .registrationRate(60)
                .newConsultationCount(81)
                .newRegistrationCount(47)
                .nonRegisteredCount(34)
                .serviceConsultationRates(List.of())
                .inflowPathRates(List.of())
                .build();

        when(service.getSummary(storeId, "2026-10")).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<CustomerManagementSummaryResponse>> response =
                controller.getSummary(storeId, "2026-10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        verify(service).getSummary(storeId, "2026-10");
    }
}
