package com.fitback.domain.inquiry.controller;

import com.fitback.domain.inquiry.service.InquiryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InquiryControllerTest {

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
}
