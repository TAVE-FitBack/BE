package com.fitback.domain.inquiry.service;

import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.inquiry.client.AiInquiryClient;
import com.fitback.domain.inquiry.dto.request.AiInquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.request.InquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.inquiry.exception.InquiryErrorCode;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private InflowPathOptionRepository inflowPathOptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiInquiryClient aiInquiryClient;

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(
                serviceRepository,
                inflowPathOptionRepository,
                userRepository,
                aiInquiryClient
        );
    }

    @Test
    @DisplayName("문의 등록 초기 데이터 조회 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getNewInquiryDataStoreNotAssigned() {
        assertThatThrownBy(() -> inquiryService.getNewInquiryData(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("문의 등록 초기 데이터로 활성 서비스, 활성 문의 경로 옵션, 상담자, 문의 상태 목록을 반환한다")
    void getNewInquiryData() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();

        Store store = Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        Service service = Service.builder()
                .id(serviceId)
                .store(store)
                .name("PT")
                .active(true)
                .build();
        InflowPathOption inflowPathOption = InflowPathOption.builder()
                .id(inflowPathId)
                .store(store)
                .name("워크인")
                .displayOrder(1)
                .active(true)
                .build();
        User counselor = User.builder()
                .id(counselorId)
                .store(store)
                .email("coach@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();

        when(serviceRepository.findAllByStoreIdAndActiveTrue(storeId))
                .thenReturn(List.of(service));
        when(inflowPathOptionRepository.findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId))
                .thenReturn(List.of(inflowPathOption));
        when(userRepository.findAllByStore_Id(storeId))
                .thenReturn(List.of(counselor));

        InquiryNewResponse response = inquiryService.getNewInquiryData(storeId);

        assertThat(response.getServices()).hasSize(1);
        assertThat(response.getServices().get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(response.getServices().get(0).getName()).isEqualTo("PT");
        assertThat(response.getInflowPaths()).hasSize(1);
        assertThat(response.getInflowPaths().get(0).getInflowPathId()).isEqualTo(inflowPathId);
        assertThat(response.getInflowPaths().get(0).getName()).isEqualTo("워크인");
        assertThat(response.getInflowPaths().get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(response.getCounselors()).hasSize(1);
        assertThat(response.getCounselors().get(0).getUserId()).isEqualTo(counselorId);
        assertThat(response.getCounselors().get(0).getName()).isEqualTo("김코치");
        assertThat(response.getInquiryStatuses())
                .extracting("status")
                .containsExactly(
                        InquiryStatus.RECEIVED,
                        InquiryStatus.VISIT_SCHEDULED,
                        InquiryStatus.VISIT_CANCELED
                );
        assertThat(response.getInquiryStatuses())
                .extracting("label")
                .containsExactly("문의 접수", "방문 예정", "방문 취소");

        verify(serviceRepository).findAllByStoreIdAndActiveTrue(storeId);
        verify(inflowPathOptionRepository).findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId);
        verify(userRepository).findAllByStore_Id(storeId);
    }

    @Test
    @DisplayName("문의 AI 중간 점검 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void checkPreviewStoreNotAssigned() {
        InquiryCheckPreviewRequest request = checkPreviewRequest(UUID.randomUUID(), InquiryStatus.RECEIVED);

        assertThatThrownBy(() -> inquiryService.checkPreview(null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, aiInquiryClient);
    }

    @Test
    @DisplayName("문의 AI 중간 점검 시 선택한 서비스가 없으면 SERVICE_NOT_FOUND 예외가 발생한다")
    void checkPreviewServiceNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        InquiryCheckPreviewRequest request = checkPreviewRequest(serviceId, InquiryStatus.VISIT_SCHEDULED);

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.checkPreview(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.SERVICE_NOT_FOUND);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verifyNoInteractions(aiInquiryClient);
    }

    @Test
    @DisplayName("문의 AI 중간 점검 시 CONVERTED 상태는 INVALID_INPUT_VALUE 예외가 발생한다")
    void checkPreviewConvertedStatusRejected() {
        UUID storeId = UUID.randomUUID();
        InquiryCheckPreviewRequest request = checkPreviewRequest(UUID.randomUUID(), InquiryStatus.CONVERTED);

        assertThatThrownBy(() -> inquiryService.checkPreview(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(serviceRepository, aiInquiryClient);
    }

    @Test
    @DisplayName("문의 AI 중간 점검은 FastAPI 요청값을 구성하고 AI 응답을 그대로 반환한다")
    void checkPreview() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        InquiryCheckPreviewRequest request = checkPreviewRequest(serviceId, InquiryStatus.RECEIVED);
        Service service = Service.builder()
                .id(serviceId)
                .name("PT")
                .active(true)
                .build();
        Map<String, Object> aiResponse = Map.of(
                "overallStatus", "NEEDS_IMPROVEMENT",
                "suggestion", "응대 내용을 추가하세요."
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(aiInquiryClient.checkPreview(any(AiInquiryCheckPreviewRequest.class)))
                .thenReturn(aiResponse);

        Map<String, Object> response = inquiryService.checkPreview(storeId, request);

        assertThat(response).isEqualTo(aiResponse);

        ArgumentCaptor<AiInquiryCheckPreviewRequest> aiRequestCaptor =
                ArgumentCaptor.forClass(AiInquiryCheckPreviewRequest.class);
        verify(aiInquiryClient).checkPreview(aiRequestCaptor.capture());
        AiInquiryCheckPreviewRequest aiRequest = aiRequestCaptor.getValue();
        assertThat(aiRequest.getRawText()).isEqualTo("문의 원문");
        assertThat(aiRequest.getServiceName()).isEqualTo("PT");
        assertThat(aiRequest.getInquiryStatus()).isEqualTo(InquiryStatus.RECEIVED);
        assertThat(aiRequest.getCustomerInfo().getName()).isEqualTo("김고객");
        assertThat(aiRequest.getCustomerInfo().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(aiRequest.getCustomerInfo().getBirthDate()).isEqualTo(LocalDate.of(1995, 1, 1));

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verifyNoInteractions(inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("문의 AI 중간 점검 시 AI 서버 호출 실패는 AI_CHECK_FAILED 예외가 전파된다")
    void checkPreviewAiFailed() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        InquiryCheckPreviewRequest request = checkPreviewRequest(serviceId, InquiryStatus.RECEIVED);
        Service service = Service.builder()
                .id(serviceId)
                .name("PT")
                .active(true)
                .build();

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(aiInquiryClient.checkPreview(any(AiInquiryCheckPreviewRequest.class)))
                .thenThrow(new BusinessException(InquiryErrorCode.AI_CHECK_FAILED));

        assertThatThrownBy(() -> inquiryService.checkPreview(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.AI_CHECK_FAILED);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(aiInquiryClient).checkPreview(any(AiInquiryCheckPreviewRequest.class));
        verifyNoInteractions(inflowPathOptionRepository, userRepository);
    }

    private InquiryCheckPreviewRequest checkPreviewRequest(UUID serviceId, InquiryStatus inquiryStatus) {
        InquiryCheckPreviewRequest request = new InquiryCheckPreviewRequest();
        InquiryCheckPreviewRequest.CustomerInfo customer = new InquiryCheckPreviewRequest.CustomerInfo();
        InquiryCheckPreviewRequest.InquiryInfo inquiry = new InquiryCheckPreviewRequest.InquiryInfo();

        ReflectionTestUtils.setField(customer, "name", "김고객");
        ReflectionTestUtils.setField(customer, "gender", Gender.FEMALE);
        ReflectionTestUtils.setField(customer, "birthDate", LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(customer, "phoneNum", "010-1234-5678");
        ReflectionTestUtils.setField(inquiry, "serviceId", serviceId);
        ReflectionTestUtils.setField(inquiry, "inquiryStatus", inquiryStatus);
        ReflectionTestUtils.setField(inquiry, "rawText", "문의 원문");
        ReflectionTestUtils.setField(request, "customer", customer);
        ReflectionTestUtils.setField(request, "inquiry", inquiry);

        return request;
    }
}
