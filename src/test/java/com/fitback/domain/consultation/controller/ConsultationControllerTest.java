package com.fitback.domain.consultation.controller;

import com.fitback.domain.consultation.dto.request.ConsultationCreateRequest;
import com.fitback.domain.consultation.dto.response.ConsultationCreateResponse;
import com.fitback.domain.consultation.service.ConsultationService;
import com.fitback.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultationControllerTest {

    @Test
    @DisplayName("상담 등록 API는 AI 분석 완료를 기다리지 않고 201 Created를 반환한다")
    void createConsultationReturnsCreated() {
        ConsultationService consultationService = mock(ConsultationService.class);
        ConsultationController consultationController = new ConsultationController(consultationService);
        UUID storeId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ConsultationCreateRequest request = new ConsultationCreateRequest();
        ConsultationCreateResponse serviceResponse = ConsultationCreateResponse.builder()
                .consultationId(consultationId)
                .customerId(customerId)
                .sessionNo(1)
                .redirectUrl("/customers/" + customerId + "/detail")
                .build();

        List<MultipartFile> materials = List.of();
        when(consultationService.createConsultation(storeId, request, materials)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<ConsultationCreateResponse>> response =
                consultationController.createConsultation(storeId, request, materials);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(serviceResponse);
        verify(consultationService).createConsultation(storeId, request, materials);
    }
}
