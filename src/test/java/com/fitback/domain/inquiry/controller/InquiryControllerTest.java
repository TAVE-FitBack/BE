package com.fitback.domain.inquiry.controller;

import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.inquiry.dto.request.InquiryCreateRequest;
import com.fitback.domain.inquiry.dto.response.InquiryCreateResponse;
import com.fitback.domain.inquiry.dto.response.InquiryConvertToConsultationResponse;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.inquiry.service.InquiryService;
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

class InquiryControllerTest {

    @Test
    @DisplayName("문의 등록 API는 multipart request와 materials를 받아 201 Created를 반환한다")
    void createInquiryReturnsCreated() {
        InquiryService inquiryService = mock(InquiryService.class);
        InquiryController inquiryController = new InquiryController(inquiryService);
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        InquiryCreateRequest request = mock(InquiryCreateRequest.class);
        List<MultipartFile> materials = List.of(mock(MultipartFile.class));
        InquiryCreateResponse serviceResponse = InquiryCreateResponse.builder()
                .inquiryId(inquiryId)
                .redirectUrl("/customers/manage?tab=inquiry")
                .build();

        when(inquiryService.createInquiry(storeId, request, materials))
                .thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<InquiryCreateResponse>> response =
                inquiryController.createInquiry(storeId, request, materials);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isSameAs(serviceResponse);
        verify(inquiryService).createInquiry(storeId, request, materials);
    }

    @Test
    @DisplayName("문의 삭제 API는 204 No Content를 반환한다")
    void deleteInquiryReturnsNoContent() {
        InquiryService inquiryService = mock(InquiryService.class);
        InquiryController inquiryController = new InquiryController(inquiryService);
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();

        ResponseEntity<Void> response = inquiryController.deleteInquiry(storeId, inquiryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(inquiryService).deleteInquiry(storeId, inquiryId);
    }

    @Test
    @DisplayName("문의 상담 전환 API는 Request Body 없이 201 Created와 전환 결과를 반환한다")
    void convertToConsultationReturnsCreated() {
        InquiryService inquiryService = mock(InquiryService.class);
        InquiryController inquiryController = new InquiryController(inquiryService);
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        InquiryConvertToConsultationResponse serviceResponse =
                InquiryConvertToConsultationResponse.builder()
                        .inquiryId(inquiryId)
                        .customerId(customerId)
                        .consultationId(consultationId)
                        .sessionNo(1)
                        .inquiryStatus(InquiryStatus.CONVERTED)
                        .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                        .redirectUrl("/customers/manage?tab=consultation&customerId=" + customerId)
                        .build();

        when(inquiryService.convertInquiry(storeId, inquiryId)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<InquiryConvertToConsultationResponse>> response =
                inquiryController.convertToConsultation(storeId, inquiryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isSameAs(serviceResponse);
        verify(inquiryService).convertInquiry(storeId, inquiryId);
    }
}
