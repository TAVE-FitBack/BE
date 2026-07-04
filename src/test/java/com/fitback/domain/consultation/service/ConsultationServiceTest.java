package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCreateRequest;
import com.fitback.domain.consultation.dto.response.ConsultationCreateResponse;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.dto.response.ConsultationCustomerSearchResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.InterestServiceRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InflowPathOptionRepository inflowPathOptionRepository;

    @Mock
    private InterestServiceRepository interestServiceRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private CustomerActivityTimelineRepository customerActivityTimelineRepository;

    @Mock
    private AiConsultationClient aiConsultationClient;

    private ConsultationService consultationService;

    @BeforeEach
    void setUp() {
        consultationService = new ConsultationService(
                serviceRepository,
                userRepository,
                customerRepository,
                inflowPathOptionRepository,
                interestServiceRepository,
                consultationRepository,
                customerActivityTimelineRepository,
                aiConsultationClient
        );
    }

    @Test
    @DisplayName("상담 등록 초기 데이터 조회 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getNewConsultationDataStoreNotAssigned() {
        assertThatThrownBy(() -> consultationService.getNewConsultationData(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, inflowPathOptionRepository, userRepository);
    }

    @Test
    @DisplayName("상담 등록 초기 데이터로 활성 서비스, 활성 방문경로 옵션, 상담자 목록을 반환한다")
    void getNewConsultationData() {
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
                .name("네이버 검색")
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

        ConsultationNewResponse response = consultationService.getNewConsultationData(storeId);

        assertThat(response.getServices()).hasSize(1);
        assertThat(response.getServices().get(0).getServiceId()).isEqualTo(serviceId);
        assertThat(response.getServices().get(0).getName()).isEqualTo("PT");
        assertThat(response.getInflowPaths()).hasSize(1);
        assertThat(response.getInflowPaths().get(0).getInflowPathId()).isEqualTo(inflowPathId);
        assertThat(response.getInflowPaths().get(0).getName()).isEqualTo("네이버 검색");
        assertThat(response.getInflowPaths().get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(response.getCounselors()).hasSize(1);
        assertThat(response.getCounselors().get(0).getUserId()).isEqualTo(counselorId);
        assertThat(response.getCounselors().get(0).getName()).isEqualTo("김코치");

        verify(serviceRepository).findAllByStoreIdAndActiveTrue(storeId);
        verify(inflowPathOptionRepository).findAllByStoreIdAndActiveTrueOrderByDisplayOrderAsc(storeId);
        verify(userRepository).findAllByStore_Id(storeId);
    }

    @Test
    @DisplayName("연락처 검색 시 phone이 비어 있으면 INVALID_INPUT_VALUE 예외가 발생한다")
    void searchCustomerByPhoneBlankPhone() {
        UUID storeId = UUID.randomUUID();

        assertThatThrownBy(() -> consultationService.searchCustomerByPhone(storeId, " "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(customerRepository, consultationRepository);
    }

    @Test
    @DisplayName("연락처 검색 결과 기존 고객이 없으면 exists=false와 null 데이터를 반환한다")
    void searchCustomerByPhoneNotFound() {
        UUID storeId = UUID.randomUUID();
        String phone = "010-0000-0000";

        when(customerRepository.findByPhoneNumAndStoreId(phone, storeId))
                .thenReturn(Optional.empty());

        ConsultationCustomerSearchResponse response = consultationService.searchCustomerByPhone(storeId, phone);

        assertThat(response.isExists()).isFalse();
        assertThat(response.getCustomer()).isNull();
        assertThat(response.getRedirectUrl()).isNull();

        verify(customerRepository).findByPhoneNumAndStoreId(phone, storeId);
        verifyNoInteractions(consultationRepository);
    }

    @Test
    @DisplayName("연락처 검색 결과 기존 고객이 있으면 기본정보와 고객 상세 redirectUrl을 반환한다")
    void searchCustomerByPhoneFound() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        String phone = "010-1234-5678";
        LocalDate birthDate = LocalDate.of(1995, 1, 1);
        LocalDate latestConsultAt = LocalDate.of(2026, 6, 30);

        Store store = Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
        InflowPathOption inflowPathOption = InflowPathOption.builder()
                .id(inflowPathId)
                .store(store)
                .name("네이버 검색")
                .displayOrder(1)
                .active(true)
                .build();
        Customer customer = Customer.builder()
                .id(customerId)
                .store(store)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(birthDate)
                .phoneNum(phone)
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(CustomerStatus.PENDING)
                .registeredService(null)
                .firstConsultAt(latestConsultAt)
                .latestConsultAt(latestConsultAt)
                .build();

        when(customerRepository.findByPhoneNumAndStoreId(phone, storeId))
                .thenReturn(Optional.of(customer));

        ConsultationCustomerSearchResponse response = consultationService.searchCustomerByPhone(storeId, phone);

        assertThat(response.isExists()).isTrue();
        assertThat(response.getRedirectUrl()).isEqualTo("/customers/" + customerId + "/detail");
        assertThat(response.getCustomer()).isNotNull();
        assertThat(response.getCustomer().getCustomerId()).isEqualTo(customerId);
        assertThat(response.getCustomer().getName()).isEqualTo("김고객");
        assertThat(response.getCustomer().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getCustomer().getBirthDate()).isEqualTo(birthDate);
        assertThat(response.getCustomer().getPhoneNum()).isEqualTo(phone);
        assertThat(response.getCustomer().getRegisteredServiceId()).isNull();
        assertThat(response.getCustomer().getStatus()).isEqualTo(CustomerStatus.PENDING);
        assertThat(response.getCustomer().getPreferredContactChannel()).isEqualTo(PreferredContactChannel.KAKAO);
        assertThat(response.getCustomer().getInflowPathId()).isEqualTo(inflowPathId);
        assertThat(response.getCustomer().getLatestConsultAt()).isEqualTo(latestConsultAt);

        verify(customerRepository).findByPhoneNumAndStoreId(phone, storeId);
        verifyNoInteractions(consultationRepository);
    }

    @Test
    @DisplayName("AI 중간 점검 시 선택한 서비스가 없으면 SERVICE_NOT_FOUND 예외가 발생한다")
    void checkPreviewServiceNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ConsultationCheckPreviewRequest request = checkPreviewRequest(serviceId);

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.checkPreview(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.SERVICE_NOT_FOUND);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verifyNoInteractions(aiConsultationClient);
    }

    @Test
    @DisplayName("AI 중간 점검 시 비활성 또는 타매장 서비스는 SERVICE_NOT_FOUND 예외가 발생한다")
    void checkPreviewInactiveOrOtherStoreService() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ConsultationCheckPreviewRequest request = checkPreviewRequest(serviceId);

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.checkPreview(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.SERVICE_NOT_FOUND);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verifyNoInteractions(aiConsultationClient);
    }

    @Test
    @DisplayName("AI 중간 점검 시 AI 서버 호출에 실패하면 AI_CHECK_FAILED 예외가 전파된다")
    void checkPreviewAiFailed() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ConsultationCheckPreviewRequest request = checkPreviewRequest(serviceId);
        Service service = Service.builder()
                .id(serviceId)
                .name("PT")
                .active(true)
                .build();

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(aiConsultationClient.checkPreview(any(AiCheckPreviewRequest.class)))
                .thenThrow(new BusinessException(ConsultationErrorCode.AI_CHECK_FAILED));

        assertThatThrownBy(() -> consultationService.checkPreview(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.AI_CHECK_FAILED);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(aiConsultationClient).checkPreview(any(AiCheckPreviewRequest.class));
        verifyNoInteractions(customerRepository, consultationRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("AI 중간 점검 결과는 DB에 저장하지 않고 AI 응답을 그대로 반환한다")
    void checkPreviewDoesNotSaveAiResult() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ConsultationCheckPreviewRequest request = checkPreviewRequest(serviceId);
        Service service = Service.builder()
                .id(serviceId)
                .name("PT")
                .active(true)
                .build();
        Map<String, Object> aiResponse = Map.of(
                "overallStatus", "SATISFIED",
                "suggestion", "충분합니다."
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(aiConsultationClient.checkPreview(any(AiCheckPreviewRequest.class)))
                .thenReturn(aiResponse);

        Map<String, Object> response = consultationService.checkPreview(storeId, request);

        assertThat(response).isEqualTo(aiResponse);
        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(aiConsultationClient).checkPreview(any(AiCheckPreviewRequest.class));
        verifyNoInteractions(customerRepository, consultationRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("상담 등록은 customerId 없이 신규 고객을 INSERT하고 최초 상담으로 저장한다")
    void createConsultationCreatesNewCustomerOnly() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        OffsetDateTime consultedAt = OffsetDateTime.parse("2026-06-30T14:00:00+09:00");

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
        InflowPathOption inflowPathOption = InflowPathOption.builder()
                .id(inflowPathId)
                .store(store)
                .name("네이버 검색")
                .displayOrder(1)
                .active(true)
                .build();
        Customer savedCustomer = Customer.builder()
                .id(customerId)
                .store(store)
                .registeredService(service)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(CustomerStatus.REGISTERED)
                .firstConsultAt(consultedAt.toLocalDate())
                .latestConsultAt(consultedAt.toLocalDate())
                .build();
        Consultation savedConsultation = Consultation.builder()
                .id(consultationId)
                .customer(savedCustomer)
                .user(counselor)
                .consultedService(service)
                .consultedAt(consultedAt)
                .sessionNo(1)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText("상담 원문")
                .build();
        ConsultationCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                ConsultationRegistrationStatus.REGISTERED,
                consultedAt
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.of(counselor));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.of(inflowPathOption));
        when(customerRepository.findByPhoneNumAndStoreId("010-1234-5678", storeId))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class)))
                .thenReturn(savedCustomer);
        when(consultationRepository.save(any(Consultation.class)))
                .thenReturn(savedConsultation);

        ConsultationCreateResponse response = consultationService.createConsultation(storeId, request);

        assertThat(response.getConsultationId()).isEqualTo(consultationId);
        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getSessionNo()).isEqualTo(1);
        assertThat(response.getRedirectUrl()).isEqualTo("/customers/" + customerId + "/detail");

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        ArgumentCaptor<Consultation> consultationCaptor = ArgumentCaptor.forClass(Consultation.class);
        verify(customerRepository).save(customerCaptor.capture());
        verify(consultationRepository).save(consultationCaptor.capture());

        Customer customerToSave = customerCaptor.getValue();
        assertThat(customerToSave.getName()).isEqualTo("김고객");
        assertThat(customerToSave.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(customerToSave.getBirthDate()).isEqualTo(LocalDate.of(1995, 1, 1));
        assertThat(customerToSave.getPhoneNum()).isEqualTo("010-1234-5678");
        assertThat(customerToSave.getPreferredContactChannel()).isEqualTo(PreferredContactChannel.KAKAO);
        assertThat(customerToSave.getInflowPathOption()).isEqualTo(inflowPathOption);
        assertThat(customerToSave.getStatus()).isEqualTo(CustomerStatus.REGISTERED);
        assertThat(customerToSave.getRegisteredService()).isEqualTo(service);

        Consultation consultationToSave = consultationCaptor.getValue();
        assertThat(consultationToSave.getCustomer()).isEqualTo(savedCustomer);
        assertThat(consultationToSave.getSessionNo()).isEqualTo(1);
        assertThat(consultationToSave.getStage()).isEqualTo(ConsultationStage.CONSULTATION);
        assertThat(consultationToSave.getSourceType()).isEqualTo(ConsultationSourceType.DIRECT);
        assertThat(consultationToSave.getRawText()).isEqualTo("상담 원문");

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(customerRepository).findByPhoneNumAndStoreId("010-1234-5678", storeId);
        verifyNoMoreInteractions(customerRepository);
    }

    private ConsultationCheckPreviewRequest checkPreviewRequest(UUID serviceId) {
        ConsultationCheckPreviewRequest request = new ConsultationCheckPreviewRequest();
        ConsultationCheckPreviewRequest.CustomerInfo customer = new ConsultationCheckPreviewRequest.CustomerInfo();
        ConsultationCheckPreviewRequest.ConsultationInfo consultation = new ConsultationCheckPreviewRequest.ConsultationInfo();

        ReflectionTestUtils.setField(customer, "name", "김고객");
        ReflectionTestUtils.setField(customer, "gender", Gender.FEMALE);
        ReflectionTestUtils.setField(customer, "birthDate", LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(customer, "phoneNum", "010-1234-5678");
        ReflectionTestUtils.setField(consultation, "consultedServiceId", serviceId);
        ReflectionTestUtils.setField(consultation, "rawText", "운동 목적과 경험을 확인했다.");
        ReflectionTestUtils.setField(request, "customer", customer);
        ReflectionTestUtils.setField(request, "consultation", consultation);

        return request;
    }

    private ConsultationCreateRequest createRequest(
            UUID serviceId,
            UUID userId,
            UUID inflowPathId,
            ConsultationRegistrationStatus registrationStatus,
            OffsetDateTime consultedAt
    ) {
        ConsultationCreateRequest request = new ConsultationCreateRequest();
        ConsultationCreateRequest.CustomerInfo customer = new ConsultationCreateRequest.CustomerInfo();
        ConsultationCreateRequest.ConsultationInfo consultation = new ConsultationCreateRequest.ConsultationInfo();

        ReflectionTestUtils.setField(customer, "name", "김고객");
        ReflectionTestUtils.setField(customer, "gender", Gender.FEMALE);
        ReflectionTestUtils.setField(customer, "birthDate", LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(customer, "phoneNum", "010-1234-5678");
        ReflectionTestUtils.setField(customer, "preferredContactChannel", PreferredContactChannel.KAKAO);
        ReflectionTestUtils.setField(customer, "inflowPathId", inflowPathId);
        ReflectionTestUtils.setField(consultation, "consultedServiceId", serviceId);
        ReflectionTestUtils.setField(consultation, "consultedAt", consultedAt);
        ReflectionTestUtils.setField(consultation, "registrationStatus", registrationStatus);
        ReflectionTestUtils.setField(consultation, "userId", userId);
        ReflectionTestUtils.setField(consultation, "rawText", "상담 원문");
        ReflectionTestUtils.setField(request, "customer", customer);
        ReflectionTestUtils.setField(request, "consultation", consultation);

        return request;
    }
}
