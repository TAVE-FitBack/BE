package com.fitback.domain.inquiry.service;

import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.InterestService;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.InterestServiceRepository;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.inquiry.client.AiInquiryClient;
import com.fitback.domain.inquiry.dto.request.AiInquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.request.InquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.request.InquiryCreateRequest;
import com.fitback.domain.inquiry.dto.response.InquiryCreateResponse;
import com.fitback.domain.inquiry.dto.response.InquiryConvertToConsultationResponse;
import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.entity.Inquiry;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.inquiry.exception.InquiryErrorCode;
import com.fitback.domain.inquiry.repository.InquiryRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
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
    private InquiryRepository inquiryRepository;

    @Mock
    private AiInquiryClient aiInquiryClient;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InterestServiceRepository interestServiceRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private CustomerActivityTimelineRepository customerActivityTimelineRepository;

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(
                serviceRepository,
                inflowPathOptionRepository,
                userRepository,
                inquiryRepository,
                aiInquiryClient,
                customerRepository,
                interestServiceRepository,
                consultationRepository,
                customerActivityTimelineRepository
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

    @ParameterizedTest
    @EnumSource(
            value = InquiryStatus.class,
            names = {"RECEIVED", "VISIT_SCHEDULED", "VISIT_CANCELED"}
    )
    @DisplayName("상담으로 전환되지 않은 문의는 물리 삭제한다")
    void deleteInquiry(InquiryStatus inquiryStatus) {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .inquiryStatus(inquiryStatus)
                .build();

        when(inquiryRepository.findByIdAndStore_Id(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));

        inquiryService.deleteInquiry(storeId, inquiryId);

        verify(inquiryRepository).findByIdAndStore_Id(inquiryId, storeId);
        verify(inquiryRepository).delete(inquiry);
    }

    @Test
    @DisplayName("문의 삭제 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void deleteInquiryStoreNotAssigned() {
        assertThatThrownBy(() -> inquiryService.deleteInquiry(null, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(inquiryRepository);
    }

    @Test
    @DisplayName("문의가 없거나 다른 매장 소속이면 INQUIRY_NOT_FOUND 예외가 발생한다")
    void deleteInquiryNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();

        when(inquiryRepository.findByIdAndStore_Id(inquiryId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.deleteInquiry(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INQUIRY_NOT_FOUND);

        verify(inquiryRepository).findByIdAndStore_Id(inquiryId, storeId);
        verify(inquiryRepository, never()).delete(any(Inquiry.class));
    }

    @Test
    @DisplayName("이미 상담으로 전환된 문의는 삭제하지 않는다")
    void deleteInquiryAlreadyConverted() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .inquiryStatus(InquiryStatus.CONVERTED)
                .build();

        when(inquiryRepository.findByIdAndStore_Id(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryService.deleteInquiry(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INQUIRY_ALREADY_CONVERTED);

        verify(inquiryRepository).findByIdAndStore_Id(inquiryId, storeId);
        verify(inquiryRepository, never()).delete(any(Inquiry.class));
    }

    @Test
    @DisplayName("상담 전환용 문의를 비관적 잠금으로 조회하고 서비스, 유입경로, 문의 담당자를 검증한다")
    void loadInquiryForConversion() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        Store store = Store.builder().id(storeId).build();
        Service service = Service.builder()
                .id(serviceId)
                .store(store)
                .active(true)
                .build();
        InflowPathOption inflowPath = InflowPathOption.builder()
                .id(inflowPathId)
                .store(store)
                .active(true)
                .build();
        User counselor = User.builder()
                .id(counselorId)
                .store(store)
                .build();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .store(store)
                .service(service)
                .inflowPathOption(inflowPath)
                .user(counselor)
                .inquiryStatus(InquiryStatus.VISIT_SCHEDULED)
                .build();

        when(inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPath));
        when(userRepository.findByIdAndStore_Id(counselorId, storeId))
                .thenReturn(Optional.of(counselor));

        Inquiry result = inquiryService.loadInquiryForConversion(storeId, inquiryId);

        assertThat(result).isSameAs(inquiry);
        assertThat(result.getUser()).isSameAs(counselor);
        verify(inquiryRepository).findByIdAndStoreIdForUpdate(inquiryId, storeId);
        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(inflowPathOptionRepository).findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId);
        verify(userRepository).findByIdAndStore_Id(counselorId, storeId);
    }

    @Test
    @DisplayName("상담 전환용 문의가 없거나 다른 매장 소속이면 INQUIRY_NOT_FOUND 예외가 발생한다")
    void loadInquiryForConversionNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();

        when(inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.loadInquiryForConversion(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INQUIRY_NOT_FOUND);

        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("상담 전환용 문의가 이미 CONVERTED이면 잠금 획득 후 전환을 차단한다")
    void loadInquiryForConversionAlreadyConverted() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .inquiryStatus(InquiryStatus.CONVERTED)
                .build();

        when(inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryService.loadInquiryForConversion(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INQUIRY_ALREADY_CONVERTED);

        verify(inquiryRepository).findByIdAndStoreIdForUpdate(inquiryId, storeId);
        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("상담 전환 시 문의의 서비스가 현재 매장 활성 서비스가 아니면 SERVICE_NOT_FOUND 예외가 발생한다")
    void loadInquiryForConversionServiceNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Inquiry inquiry = conversionInquiry(inquiryId, serviceId, UUID.randomUUID(), UUID.randomUUID());

        when(inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.loadInquiryForConversion(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.SERVICE_NOT_FOUND);

        verifyNoInteractions(inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("상담 전환 시 문의의 유입경로가 현재 매장 활성 경로가 아니면 INFLOW_PATH_NOT_FOUND 예외가 발생한다")
    void loadInquiryForConversionInflowPathNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        Inquiry inquiry = conversionInquiry(inquiryId, serviceId, inflowPathId, UUID.randomUUID());

        when(inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(inquiry.getService()));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.loadInquiryForConversion(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INFLOW_PATH_NOT_FOUND);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("상담 전환 시 inquiry.user_id 담당자가 현재 매장 소속이 아니면 COUNSELOR_NOT_FOUND 예외가 발생한다")
    void loadInquiryForConversionCounselorNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        Inquiry inquiry = conversionInquiry(inquiryId, serviceId, inflowPathId, counselorId);

        when(inquiryRepository.findByIdAndStoreIdForUpdate(inquiryId, storeId))
                .thenReturn(Optional.of(inquiry));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(inquiry.getService()));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inquiry.getInflowPathOption()));
        when(userRepository.findByIdAndStore_Id(counselorId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.loadInquiryForConversion(storeId, inquiryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.COUNSELOR_NOT_FOUND);
    }

    @Test
    @DisplayName("동일 매장과 연락처의 고객이 없으면 문의 정보로 PENDING 고객과 관심 서비스, 첫 상담을 생성한다")
    void resolveCustomerConversionCreatesCustomerAndFirstConsultation() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        OffsetDateTime inquiredAt = OffsetDateTime.parse("2026-07-01T14:30:00+09:00");
        Store store = Store.builder().id(storeId).build();
        Service service = Service.builder().id(UUID.randomUUID()).store(store).build();
        InflowPathOption inflowPath = InflowPathOption.builder()
                .id(UUID.randomUUID())
                .store(store)
                .build();
        User counselor = User.builder().id(UUID.randomUUID()).store(store).build();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .store(store)
                .service(service)
                .user(counselor)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPath)
                .inquiryStatus(InquiryStatus.VISIT_SCHEDULED)
                .inquiredAt(inquiredAt)
                .rawText("방문 상담 문의 원문")
                .build();

        when(customerRepository.findByPhoneNumAndStoreIdForUpdate(inquiry.getPhoneNum(), storeId))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer = invocation.getArgument(0);
                    ReflectionTestUtils.setField(customer, "id", customerId);
                    return customer;
                });
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> {
                    Consultation consultation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(consultation, "id", consultationId);
                    return consultation;
                });

        InquiryConversionContext result = inquiryService.resolveCustomerConversion(inquiry);

        assertThat(result.customerCreated()).isTrue();
        assertThat(result.customer().getId()).isEqualTo(customerId);
        assertThat(result.consultation().getId()).isEqualTo(consultationId);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer customer = customerCaptor.getValue();
        assertThat(customer.getStore()).isSameAs(store);
        assertThat(customer.getRegisteredService()).isNull();
        assertThat(customer.getName()).isEqualTo(inquiry.getName());
        assertThat(customer.getGender()).isEqualTo(inquiry.getGender());
        assertThat(customer.getBirthDate()).isEqualTo(inquiry.getBirthDate());
        assertThat(customer.getPhoneNum()).isEqualTo(inquiry.getPhoneNum());
        assertThat(customer.getPreferredContactChannel()).isEqualTo(inquiry.getPreferredContactChannel());
        assertThat(customer.getInflowPathOption()).isSameAs(inflowPath);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.PENDING);
        assertThat(customer.getRegisteredAt()).isNull();
        assertThat(customer.getFirstConsultAt()).isEqualTo(inquiredAt.toLocalDate());
        assertThat(customer.getLatestConsultAt()).isEqualTo(inquiredAt.toLocalDate());

        ArgumentCaptor<InterestService> interestServiceCaptor = ArgumentCaptor.forClass(InterestService.class);
        verify(interestServiceRepository).save(interestServiceCaptor.capture());
        assertThat(interestServiceCaptor.getValue().getCustomer()).isSameAs(customer);
        assertThat(interestServiceCaptor.getValue().getService()).isSameAs(service);

        ArgumentCaptor<Consultation> consultationCaptor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(consultationCaptor.capture());
        Consultation consultation = consultationCaptor.getValue();
        assertThat(consultation.getCustomer()).isSameAs(customer);
        assertThat(consultation.getUser()).isSameAs(counselor);
        assertThat(consultation.getConsultedService()).isSameAs(service);
        assertThat(consultation.getConsultedAt()).isEqualTo(inquiredAt);
        assertThat(consultation.getSessionNo()).isEqualTo(1);
        assertThat(consultation.getStage()).isEqualTo(ConsultationStage.CONSULTATION);
        assertThat(consultation.getSourceType()).isEqualTo(ConsultationSourceType.INQUIRY);
        assertThat(consultation.getRawText()).isEqualTo(inquiry.getRawText());
        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);
    }

    @Test
    @DisplayName("동일 매장과 연락처의 기존 고객이 있으면 정보를 유지하고 다음 회차 상담을 생성한다")
    void resolveCustomerConversionCreatesConsultationForExistingCustomer() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        OffsetDateTime inquiredAt = OffsetDateTime.parse("2026-07-02T10:00:00+09:00");
        Store store = Store.builder().id(storeId).build();
        Service service = Service.builder().id(serviceId).store(store).build();
        User counselor = User.builder().id(UUID.randomUUID()).store(store).build();
        Customer existingCustomer = Customer.builder()
                .id(customerId)
                .store(store)
                .name("기존 이름")
                .phoneNum("010-1234-5678")
                .status(CustomerStatus.PENDING)
                .firstConsultAt(LocalDate.of(2026, 6, 1))
                .latestConsultAt(LocalDate.of(2026, 6, 15))
                .build();
        Inquiry inquiry = Inquiry.builder()
                .store(store)
                .service(service)
                .user(counselor)
                .name("문의 이름")
                .phoneNum("010-1234-5678")
                .inquiredAt(inquiredAt)
                .rawText("기존 고객 문의 원문")
                .build();
        Consultation previousConsultation = Consultation.builder()
                .customer(existingCustomer)
                .sessionNo(2)
                .build();

        when(customerRepository.findByPhoneNumAndStoreIdForUpdate(inquiry.getPhoneNum(), storeId))
                .thenReturn(Optional.of(existingCustomer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(previousConsultation));
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(interestServiceRepository.existsByCustomerIdAndServiceId(customerId, serviceId))
                .thenReturn(true);

        InquiryConversionContext result = inquiryService.resolveCustomerConversion(inquiry);

        assertThat(result.customerCreated()).isFalse();
        assertThat(result.customer()).isSameAs(existingCustomer);
        assertThat(result.customer().getName()).isEqualTo("기존 이름");
        assertThat(result.customer().getFirstConsultAt()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.customer().getLatestConsultAt()).isEqualTo(inquiredAt.toLocalDate());

        Consultation consultation = result.consultation();
        assertThat(consultation.getCustomer()).isSameAs(existingCustomer);
        assertThat(consultation.getUser()).isSameAs(counselor);
        assertThat(consultation.getConsultedService()).isSameAs(service);
        assertThat(consultation.getConsultedAt()).isEqualTo(inquiredAt);
        assertThat(consultation.getSessionNo()).isEqualTo(3);
        assertThat(consultation.getStage()).isEqualTo(ConsultationStage.CONSULTATION);
        assertThat(consultation.getSourceType()).isEqualTo(ConsultationSourceType.INQUIRY);
        assertThat(consultation.getRawText()).isEqualTo(inquiry.getRawText());
        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);

        verify(customerRepository, never()).save(any(Customer.class));
        verify(interestServiceRepository).existsByCustomerIdAndServiceId(customerId, serviceId);
        verify(interestServiceRepository, never()).save(any(InterestService.class));
    }

    @Test
    @DisplayName("기존 고객에게 문의 서비스 관심 정보가 없으면 한 번만 추가한다")
    void resolveCustomerConversionAddsMissingInterestService() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Store store = Store.builder().id(storeId).build();
        Service service = Service.builder().id(serviceId).store(store).build();
        Customer existingCustomer = Customer.builder()
                .id(customerId)
                .store(store)
                .latestConsultAt(LocalDate.of(2026, 6, 1))
                .build();
        Inquiry inquiry = Inquiry.builder()
                .store(store)
                .service(service)
                .user(User.builder().id(UUID.randomUUID()).store(store).build())
                .phoneNum("010-1234-5678")
                .inquiredAt(OffsetDateTime.parse("2026-07-02T10:00:00+09:00"))
                .rawText("문의 원문")
                .build();

        when(customerRepository.findByPhoneNumAndStoreIdForUpdate(inquiry.getPhoneNum(), storeId))
                .thenReturn(Optional.of(existingCustomer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.empty());
        when(consultationRepository.save(any(Consultation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(interestServiceRepository.existsByCustomerIdAndServiceId(customerId, serviceId))
                .thenReturn(false);

        InquiryConversionContext result = inquiryService.resolveCustomerConversion(inquiry);

        assertThat(result.consultation().getSessionNo()).isEqualTo(1);
        ArgumentCaptor<InterestService> captor = ArgumentCaptor.forClass(InterestService.class);
        verify(interestServiceRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomer()).isSameAs(existingCustomer);
        assertThat(captor.getValue().getService()).isSameAs(service);
    }

    @Test
    @DisplayName("신규 고객 전환 완료 시 문의 상태와 전환 정보를 저장하고 타임라인에 신규 생성 여부를 기록한다")
    void convertInquiryUpdatesInquiryAndSavesNewCustomerTimeline() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        Store store = Store.builder().id(storeId).build();
        User counselor = User.builder().id(UUID.randomUUID()).store(store).build();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .store(store)
                .user(counselor)
                .inquiryStatus(InquiryStatus.RECEIVED)
                .build();
        Customer customer = Customer.builder().id(customerId).store(store).build();
        Consultation consultation = Consultation.builder()
                .id(consultationId)
                .customer(customer)
                .sessionNo(1)
                .build();
        InquiryConversionContext context = InquiryConversionContext.newCustomer(customer, consultation);
        InquiryService service = spy(inquiryService);

        doReturn(inquiry).when(service).loadInquiryForConversion(storeId, inquiryId);
        doReturn(context).when(service).resolveCustomerConversion(inquiry);

        InquiryConvertToConsultationResponse result = service.convertInquiry(storeId, inquiryId);

        assertThat(result.getInquiryId()).isEqualTo(inquiryId);
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getConsultationId()).isEqualTo(consultationId);
        assertThat(result.getSessionNo()).isEqualTo(1);
        assertThat(result.getInquiryStatus()).isEqualTo(InquiryStatus.CONVERTED);
        assertThat(result.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);
        assertThat(result.getRedirectUrl())
                .isEqualTo("/customers/manage?tab=consultation&customerId=" + customerId);
        assertThat(inquiry.getInquiryStatus()).isEqualTo(InquiryStatus.CONVERTED);
        assertThat(inquiry.getConvertedCustomer()).isSameAs(customer);
        assertThat(inquiry.getConvertedConsultation()).isSameAs(consultation);
        assertThat(inquiry.getConvertedAt()).isNotNull();

        ArgumentCaptor<CustomerActivityTimeline> captor =
                ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(captor.capture());
        CustomerActivityTimeline timeline = captor.getValue();
        assertThat(timeline.getStore()).isSameAs(store);
        assertThat(timeline.getCustomer()).isSameAs(customer);
        assertThat(timeline.getActorUser()).isSameAs(counselor);
        assertThat(timeline.getActivityType())
                .isEqualTo(CustomerActivityType.INQUIRY_CONVERTED_TO_CONSULTATION);
        assertThat(timeline.getTitle()).isEqualTo("문의가 상담으로 전환되었습니다.");
        assertThat(timeline.getDescription()).contains("신규 고객과 첫 상담");
        assertThat(timeline.getRelatedType()).isEqualTo(ActivityRelatedType.INQUIRY);
        assertThat(timeline.getRelatedId()).isEqualTo(inquiryId);
        assertThat(timeline.getOccurredAt()).isEqualTo(inquiry.getConvertedAt());
        assertThat(timeline.getAfterValue())
                .containsEntry("newCustomerCreated", true)
                .containsEntry("customerId", customerId)
                .containsEntry("consultationId", consultationId)
                .containsEntry("sessionNo", 1);
    }

    @Test
    @DisplayName("기존 고객 전환 타임라인에는 신규 고객을 생성하지 않았음을 기록한다")
    void convertInquirySavesExistingCustomerTimeline() {
        UUID storeId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        Store store = Store.builder().id(storeId).build();
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .store(store)
                .user(User.builder().id(UUID.randomUUID()).store(store).build())
                .inquiryStatus(InquiryStatus.VISIT_SCHEDULED)
                .build();
        Customer customer = Customer.builder().id(UUID.randomUUID()).store(store).build();
        Consultation consultation = Consultation.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .sessionNo(3)
                .build();
        InquiryConversionContext context = InquiryConversionContext.existingCustomer(customer, consultation);
        InquiryService service = spy(inquiryService);

        doReturn(inquiry).when(service).loadInquiryForConversion(storeId, inquiryId);
        doReturn(context).when(service).resolveCustomerConversion(inquiry);

        service.convertInquiry(storeId, inquiryId);

        ArgumentCaptor<CustomerActivityTimeline> captor =
                ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).contains("기존 고객");
        assertThat(captor.getValue().getAfterValue())
                .containsEntry("newCustomerCreated", false)
                .containsEntry("sessionNo", 3);
    }

    @Test
    @DisplayName("문의 등록은 inquiry 테이블에 저장하고 inquiryId와 문의 탭 redirectUrl을 반환한다")
    void createInquiry() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        OffsetDateTime inquiredAt = OffsetDateTime.parse("2026-06-01T13:00:00+09:00");
        OffsetDateTime visitScheduledAt = OffsetDateTime.parse("2026-06-02T14:00:00+09:00");
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
                .id(userId)
                .store(store)
                .email("coach@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();
        Inquiry savedInquiry = Inquiry.builder()
                .id(inquiryId)
                .store(store)
                .service(service)
                .user(counselor)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .inquiryStatus(InquiryStatus.VISIT_SCHEDULED)
                .inquiredAt(inquiredAt)
                .visitScheduledAt(visitScheduledAt)
                .rawText("문의 원문")
                .build();
        InquiryCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                InquiryStatus.VISIT_SCHEDULED,
                inquiredAt,
                visitScheduledAt
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPathOption));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.of(counselor));
        when(inquiryRepository.save(any(Inquiry.class)))
                .thenReturn(savedInquiry);

        InquiryCreateResponse response = inquiryService.createInquiry(storeId, request);

        assertThat(response.getInquiryId()).isEqualTo(inquiryId);
        assertThat(response.getRedirectUrl()).isEqualTo("/customers/manage?tab=inquiry");

        ArgumentCaptor<Inquiry> inquiryCaptor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(inquiryCaptor.capture());
        Inquiry inquiryToSave = inquiryCaptor.getValue();
        assertThat(inquiryToSave.getStore()).isEqualTo(store);
        assertThat(inquiryToSave.getCustomer()).isNull();
        assertThat(inquiryToSave.getService()).isEqualTo(service);
        assertThat(inquiryToSave.getUser()).isEqualTo(counselor);
        assertThat(inquiryToSave.getName()).isEqualTo("김고객");
        assertThat(inquiryToSave.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(inquiryToSave.getBirthDate()).isEqualTo(LocalDate.of(1995, 1, 1));
        assertThat(inquiryToSave.getPhoneNum()).isEqualTo("010-1234-5678");
        assertThat(inquiryToSave.getPreferredContactChannel()).isEqualTo(PreferredContactChannel.KAKAO);
        assertThat(inquiryToSave.getInflowPathOption()).isEqualTo(inflowPathOption);
        assertThat(inquiryToSave.getInquiryStatus()).isEqualTo(InquiryStatus.VISIT_SCHEDULED);
        assertThat(inquiryToSave.getInquiredAt()).isEqualTo(inquiredAt);
        assertThat(inquiryToSave.getVisitScheduledAt()).isEqualTo(visitScheduledAt);
        assertThat(inquiryToSave.getRawText()).isEqualTo("문의 원문");
        assertThat(inquiryToSave.getConvertedCustomer()).isNull();
        assertThat(inquiryToSave.getConvertedConsultation()).isNull();
        assertThat(inquiryToSave.getConvertedAt()).isNull();

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(inflowPathOptionRepository).findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId);
        verify(userRepository).findByIdAndStore_Id(userId, storeId);
    }

    @Test
    @DisplayName("문의 등록 시 방문 예정 상태가 아니면 방문 예정일은 null로 저장한다")
    void createInquiryWithoutVisitScheduledAtWhenNotScheduled() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        OffsetDateTime inquiredAt = OffsetDateTime.parse("2026-06-01T13:00:00+09:00");
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
                .id(userId)
                .store(store)
                .email("coach@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();
        Inquiry savedInquiry = Inquiry.builder()
                .id(UUID.randomUUID())
                .store(store)
                .service(service)
                .user(counselor)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .inquiryStatus(InquiryStatus.RECEIVED)
                .inquiredAt(inquiredAt)
                .visitScheduledAt(null)
                .rawText("문의 원문")
                .build();
        InquiryCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                InquiryStatus.RECEIVED,
                inquiredAt,
                OffsetDateTime.parse("2026-06-02T14:00:00+09:00")
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPathOption));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.of(counselor));
        when(inquiryRepository.save(any(Inquiry.class)))
                .thenReturn(savedInquiry);

        inquiryService.createInquiry(storeId, request);

        ArgumentCaptor<Inquiry> inquiryCaptor = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiryRepository).save(inquiryCaptor.capture());
        assertThat(inquiryCaptor.getValue().getVisitScheduledAt()).isNull();
    }

    @Test
    @DisplayName("문의 등록 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void createInquiryStoreNotAssigned() {
        InquiryCreateRequest request = createRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                InquiryStatus.RECEIVED,
                OffsetDateTime.parse("2026-06-01T13:00:00+09:00"),
                null
        );

        assertThatThrownBy(() -> inquiryService.createInquiry(null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository, inquiryRepository);
    }

    @Test
    @DisplayName("문의 등록 시 활성 서비스가 없으면 SERVICE_NOT_FOUND 예외가 발생한다")
    void createInquiryServiceNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        InquiryCreateRequest request = createRequest(
                serviceId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                InquiryStatus.RECEIVED,
                OffsetDateTime.parse("2026-06-01T13:00:00+09:00"),
                null
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.createInquiry(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.SERVICE_NOT_FOUND);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verifyNoInteractions(inflowPathOptionRepository, userRepository, inquiryRepository);
    }

    @Test
    @DisplayName("문의 등록 시 활성 문의 경로가 없으면 INFLOW_PATH_NOT_FOUND 예외가 발생한다")
    void createInquiryInflowPathNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        InquiryCreateRequest request = createRequest(
                serviceId,
                UUID.randomUUID(),
                inflowPathId,
                InquiryStatus.RECEIVED,
                OffsetDateTime.parse("2026-06-01T13:00:00+09:00"),
                null
        );
        Service service = Service.builder()
                .id(serviceId)
                .name("PT")
                .active(true)
                .build();

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.createInquiry(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.INFLOW_PATH_NOT_FOUND);

        verify(inflowPathOptionRepository).findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId);
        verifyNoInteractions(userRepository, inquiryRepository);
    }

    @Test
    @DisplayName("문의 등록 시 상담자가 같은 매장에 없으면 COUNSELOR_NOT_FOUND 예외가 발생한다")
    void createInquiryCounselorNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        Store store = Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        InquiryCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                InquiryStatus.RECEIVED,
                OffsetDateTime.parse("2026-06-01T13:00:00+09:00"),
                null
        );
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

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPathOption));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.createInquiry(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InquiryErrorCode.COUNSELOR_NOT_FOUND);

        verify(userRepository).findByIdAndStore_Id(userId, storeId);
        verifyNoInteractions(inquiryRepository);
    }

    @Test
    @DisplayName("문의 등록 시 CONVERTED 상태는 INVALID_INPUT_VALUE 예외가 발생한다")
    void createInquiryConvertedStatusRejected() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        Store store = Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        InquiryCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                InquiryStatus.CONVERTED,
                OffsetDateTime.parse("2026-06-01T13:00:00+09:00"),
                null
        );
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
                .id(userId)
                .store(store)
                .email("coach@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPathOption));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.of(counselor));

        assertThatThrownBy(() -> inquiryService.createInquiry(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(inquiryRepository);
    }

    @Test
    @DisplayName("문의 등록 시 방문 예정 상태인데 방문 예정일이 없으면 INVALID_INPUT_VALUE 예외가 발생한다")
    void createInquiryVisitScheduledAtRequired() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        Store store = Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        InquiryCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                InquiryStatus.VISIT_SCHEDULED,
                OffsetDateTime.parse("2026-06-01T13:00:00+09:00"),
                null
        );
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
                .id(userId)
                .store(store)
                .email("coach@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPathOption));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.of(counselor));

        assertThatThrownBy(() -> inquiryService.createInquiry(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(inquiryRepository);
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

    private InquiryCreateRequest createRequest(
            UUID serviceId,
            UUID userId,
            UUID inflowPathId,
            InquiryStatus inquiryStatus,
            OffsetDateTime inquiredAt,
            OffsetDateTime visitScheduledAt
    ) {
        InquiryCreateRequest request = new InquiryCreateRequest();
        InquiryCreateRequest.CustomerInfo customer = new InquiryCreateRequest.CustomerInfo();
        InquiryCreateRequest.InquiryInfo inquiry = new InquiryCreateRequest.InquiryInfo();

        ReflectionTestUtils.setField(customer, "name", "김고객");
        ReflectionTestUtils.setField(customer, "gender", Gender.FEMALE);
        ReflectionTestUtils.setField(customer, "birthDate", LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(customer, "phoneNum", "010-1234-5678");
        ReflectionTestUtils.setField(customer, "preferredContactChannel", PreferredContactChannel.KAKAO);
        ReflectionTestUtils.setField(customer, "inflowPathId", inflowPathId);
        ReflectionTestUtils.setField(inquiry, "serviceId", serviceId);
        ReflectionTestUtils.setField(inquiry, "userId", userId);
        ReflectionTestUtils.setField(inquiry, "inquiryStatus", inquiryStatus);
        ReflectionTestUtils.setField(inquiry, "inquiredAt", inquiredAt);
        ReflectionTestUtils.setField(inquiry, "visitScheduledAt", visitScheduledAt);
        ReflectionTestUtils.setField(inquiry, "rawText", "문의 원문");
        ReflectionTestUtils.setField(request, "customer", customer);
        ReflectionTestUtils.setField(request, "inquiry", inquiry);

        return request;
    }

    private Inquiry conversionInquiry(
            UUID inquiryId,
            UUID serviceId,
            UUID inflowPathId,
            UUID counselorId
    ) {
        return Inquiry.builder()
                .id(inquiryId)
                .service(Service.builder().id(serviceId).active(true).build())
                .inflowPathOption(InflowPathOption.builder().id(inflowPathId).active(true).build())
                .user(User.builder().id(counselorId).build())
                .inquiryStatus(InquiryStatus.RECEIVED)
                .build();
    }
}
