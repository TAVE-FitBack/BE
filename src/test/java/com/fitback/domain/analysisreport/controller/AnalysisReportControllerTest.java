package com.fitback.domain.analysisreport.controller;

import com.fitback.domain.analysisreport.dto.response.AnalysisReportFollowUpResponse;
import com.fitback.domain.analysisreport.service.AnalysisReportFollowUpService;
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

class AnalysisReportControllerTest {

    @Test
    @DisplayName("후속관리 분석 리포트 API는 200 OK와 조회 결과를 반환한다")
    void getFollowUpReport() {
        AnalysisReportFollowUpService service = mock(AnalysisReportFollowUpService.class);
        AnalysisReportController controller = new AnalysisReportController(service);
        UUID storeId = UUID.randomUUID();
        String month = "2026-10";
        AnalysisReportFollowUpResponse serviceResponse = AnalysisReportFollowUpResponse.builder()
                .month(month)
                .summary(AnalysisReportFollowUpResponse.Summary.builder().build())
                .registrationChange(AnalysisReportFollowUpResponse.RegistrationChange.builder().build())
                .conversionGraph(AnalysisReportFollowUpResponse.ConversionGraph.builder()
                        .rounds(List.of())
                        .build())
                .nonConversionReasons(List.of())
                .aiRecommendations(List.of())
                .build();

        when(service.getFollowUpReport(storeId, month)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<AnalysisReportFollowUpResponse>> response =
                controller.getFollowUpReport(storeId, month);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isSameAs(serviceResponse);
        verify(service).getFollowUpReport(storeId, month);
    }
}
