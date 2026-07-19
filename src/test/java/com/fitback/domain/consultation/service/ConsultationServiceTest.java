package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.ConsultationMaterialFileData;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewItemRequest;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCreateRequest;
import com.fitback.domain.consultation.dto.response.ConsultationCreateResponse;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.dto.response.ConsultationCustomerSearchResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationMaterial;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.enums.ConsultationMaterialType;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.repository.ConsultationMaterialRepository;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.store.entity.InflowPathOption;
import com.fitback.domain.customer.entity.InterestService;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.ConversionSource;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.store.repository.InflowPathOptionRepository;
import com.fitback.domain.customer.repository.InterestServiceRepository;
import com.fitback.domain.customer.service.FollowUpConversionService;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
    private ConsultationMaterialRepository consultationMaterialRepository;

    @Mock
    private CustomerActivityTimelineRepository customerActivityTimelineRepository;

    @Mock
    private AiConsultationClient aiConsultationClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FollowUpConversionService followUpConversionService;

    @Mock
    private ConsultationMaterialFileService consultationMaterialFileService;

    @Mock
    private ConsultationSignalService consultationSignalService;

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
                consultationMaterialRepository,
                customerActivityTimelineRepository,
                aiConsultationClient,
                eventPublisher,
                followUpConversionService,
                consultationMaterialFileService,
                consultationSignalService
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
                .registeredAt(consultedAt)
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
        assertThat(customerToSave.getRegisteredAt()).isEqualTo(consultedAt);

        Consultation consultationToSave = consultationCaptor.getValue();
        assertThat(consultationToSave.getCustomer()).isEqualTo(savedCustomer);
        assertThat(consultationToSave.getSessionNo()).isEqualTo(1);
        assertThat(consultationToSave.getStage()).isEqualTo(ConsultationStage.CONSULTATION);
        assertThat(consultationToSave.getSourceType()).isEqualTo(ConsultationSourceType.DIRECT);
        assertThat(consultationToSave.getRawText()).isEqualTo("상담 원문");

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(customerRepository).findByPhoneNumAndStoreId("010-1234-5678", storeId);
        ArgumentCaptor<ConsultationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ConsultationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().consultationId()).isEqualTo(consultationId);
        verify(followUpConversionService).recordConversionIfAbsent(
                savedCustomer,
                consultedAt,
                ConversionSource.DIRECT_REGISTRATION
        );
        verify(consultationSignalService).saveSnapshot(savedConsultation, null);
        verify(aiConsultationClient, never()).analyzeConsultation(any());
        verifyNoInteractions(interestServiceRepository);
        verifyNoMoreInteractions(customerRepository);
    }

    @Test
    @DisplayName("상담 등록 요청에 aiCheckPreview가 있으면 consultation 저장 후 consultation_signal 저장 서비스를 호출한다")
    void createConsultationSavesAiCheckPreviewSnapshot() {
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
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(CustomerStatus.PENDING)
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
        AiCheckPreviewSnapshotRequest aiCheckPreview = aiCheckPreviewSnapshot();
        ConsultationCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                ConsultationRegistrationStatus.PENDING,
                consultedAt
        );
        ReflectionTestUtils.setField(request, "aiCheckPreview", aiCheckPreview);

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

        consultationService.createConsultation(storeId, request);

        InOrder inOrder = inOrder(consultationRepository, consultationSignalService);
        inOrder.verify(consultationRepository).save(any(Consultation.class));
        inOrder.verify(consultationSignalService).saveSnapshot(savedConsultation, aiCheckPreview);
    }

    @ParameterizedTest
    @EnumSource(value = ConsultationRegistrationStatus.class, names = {"PENDING", "SCHEDULED", "LOST"})
    @DisplayName("등록 완료가 아니면 확정 서비스 없이 고객을 저장하고 선택 서비스를 관심 서비스로 저장한다")
    void createConsultationCreatesInterestServiceForNonRegisteredStatus(ConsultationRegistrationStatus registrationStatus) {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        OffsetDateTime consultedAt = OffsetDateTime.parse("2026-06-30T14:00:00+09:00");
        CustomerStatus expectedStatus = switch (registrationStatus) {
            case PENDING -> CustomerStatus.PENDING;
            case SCHEDULED -> CustomerStatus.SCHEDULED;
            case LOST -> CustomerStatus.LOST;
            case REGISTERED -> throw new IllegalArgumentException("REGISTERED is not part of this test");
        };

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
                .registeredService(null)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(expectedStatus)
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
                registrationStatus,
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
        ArgumentCaptor<InterestService> interestServiceCaptor = ArgumentCaptor.forClass(InterestService.class);
        ArgumentCaptor<Consultation> consultationCaptor = ArgumentCaptor.forClass(Consultation.class);
        verify(customerRepository).save(customerCaptor.capture());
        verify(interestServiceRepository).save(interestServiceCaptor.capture());
        verify(consultationRepository).save(consultationCaptor.capture());

        Customer customerToSave = customerCaptor.getValue();
        assertThat(customerToSave.getStatus()).isEqualTo(expectedStatus);
        assertThat(customerToSave.getRegisteredService()).isNull();

        InterestService interestService = interestServiceCaptor.getValue();
        assertThat(interestService.getCustomer()).isEqualTo(savedCustomer);
        assertThat(interestService.getService()).isEqualTo(service);

        Consultation consultationToSave = consultationCaptor.getValue();
        assertThat(consultationToSave.getCustomer()).isEqualTo(savedCustomer);
        assertThat(consultationToSave.getSessionNo()).isEqualTo(1);
        assertThat(consultationToSave.getStage()).isEqualTo(ConsultationStage.CONSULTATION);
        assertThat(consultationToSave.getSourceType()).isEqualTo(ConsultationSourceType.DIRECT);
        verifyNoInteractions(followUpConversionService);
    }

    @Test
    @DisplayName("상담 등록 완료 시 고객 활동 타임라인을 저장한다")
    void createConsultationCreatesTimeline() {
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
                .registeredAt(consultedAt)
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

        consultationService.createConsultation(storeId, request);

        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());

        CustomerActivityTimeline timeline = timelineCaptor.getValue();
        assertThat(timeline.getStore()).isEqualTo(store);
        assertThat(timeline.getCustomer()).isEqualTo(savedCustomer);
        assertThat(timeline.getActorUser()).isEqualTo(counselor);
        assertThat(timeline.getActivityType()).isEqualTo(CustomerActivityType.CONSULTATION_CREATED);
        assertThat(timeline.getTitle()).isEqualTo("상담 기록 등록");
        assertThat(timeline.getDescription()).isEqualTo("고객의 최초 상담 기록이 등록되었습니다.");
        assertThat(timeline.getRelatedType()).isEqualTo(ActivityRelatedType.CONSULTATION);
        assertThat(timeline.getRelatedId()).isEqualTo(consultationId);
        assertThat(timeline.getAfterValue()).containsEntry("consultationId", consultationId);
        assertThat(timeline.getAfterValue()).containsEntry("sessionNo", 1);
        assertThat(timeline.getAfterValue()).containsEntry("stage", ConsultationStage.CONSULTATION);
        assertThat(timeline.getAfterValue()).containsEntry("sourceType", ConsultationSourceType.DIRECT);
        assertThat(timeline.getAfterValue()).containsEntry("consultedServiceId", serviceId);
        assertThat(timeline.getAfterValue()).containsEntry("customerStatus", CustomerStatus.REGISTERED);
        assertThat(timeline.getOccurredAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("신규상담 등록 시 첨부파일이 있으면 상담자료를 저장한 뒤 AI 분석 이벤트를 발행한다")
    void createConsultationSavesMaterialsBeforePublishingEvent() {
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
                .registeredService(null)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(CustomerStatus.PENDING)
                .registeredAt(null)
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
                ConsultationRegistrationStatus.PENDING,
                consultedAt
        );
        MockMultipartFile file = new MockMultipartFile(
                "materials",
                "kakao-chat.txt",
                "text/plain",
                "첨부 상담자료".getBytes()
        );
        List<MultipartFile> materials = List.of(file);
        ConsultationMaterialFileData materialData = ConsultationMaterialFileData.builder()
                .materialType(ConsultationMaterialType.OTHER)
                .title("kakao-chat")
                .originalFileName("kakao-chat.txt")
                .contentType("text/plain")
                .fileSize(file.getSize())
                .content("첨부 상담자료")
                .build();

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
        when(consultationMaterialFileService.extractMaterials(materials))
                .thenReturn(List.of(materialData));

        consultationService.createConsultation(storeId, request, materials);

        ArgumentCaptor<List<ConsultationMaterial>> materialCaptor = ArgumentCaptor.forClass(List.class);
        verify(consultationMaterialRepository).saveAll(materialCaptor.capture());
        assertThat(materialCaptor.getValue()).hasSize(1);
        ConsultationMaterial material = materialCaptor.getValue().get(0);
        assertThat(material.getStore()).isEqualTo(store);
        assertThat(material.getCustomer()).isEqualTo(savedCustomer);
        assertThat(material.getConsultation()).isEqualTo(savedConsultation);
        assertThat(material.getInquiry()).isNull();
        assertThat(material.getMaterialType()).isEqualTo(ConsultationMaterialType.OTHER);
        assertThat(material.getTitle()).isEqualTo("kakao-chat");
        assertThat(material.getOriginalFileName()).isEqualTo("kakao-chat.txt");
        assertThat(material.getContentType()).isEqualTo("text/plain");
        assertThat(material.getFileSize()).isEqualTo(file.getSize());
        assertThat(material.getContent()).isEqualTo("첨부 상담자료");
        assertThat(material.getCreatedBy()).isEqualTo(counselor);

        ArgumentCaptor<ConsultationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ConsultationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().consultationId()).isEqualTo(consultationId);

        InOrder inOrder = inOrder(consultationMaterialRepository, eventPublisher);
        inOrder.verify(consultationMaterialRepository).saveAll(any());
        inOrder.verify(eventPublisher).publishEvent(any(ConsultationCreatedEvent.class));
    }

    @Test
    @DisplayName("상담 등록 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void createConsultationStoreNotAssigned() {
        ConsultationCreateRequest request = createRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConsultationRegistrationStatus.REGISTERED,
                OffsetDateTime.parse("2026-06-30T14:00:00+09:00")
        );

        assertThatThrownBy(() -> consultationService.createConsultation(null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(serviceRepository, userRepository, inflowPathOptionRepository, customerRepository);
    }

    @Test
    @DisplayName("상담 등록 시 활성 서비스가 없으면 SERVICE_NOT_FOUND 예외가 발생한다")
    void createConsultationServiceNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ConsultationCreateRequest request = createRequest(
                serviceId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConsultationRegistrationStatus.REGISTERED,
                OffsetDateTime.parse("2026-06-30T14:00:00+09:00")
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.createConsultation(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.SERVICE_NOT_FOUND);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verifyNoInteractions(userRepository, inflowPathOptionRepository, customerRepository, consultationRepository);
    }

    @Test
    @DisplayName("상담 등록 시 상담자가 같은 매장에 없으면 COUNSELOR_NOT_FOUND 예외가 발생한다")
    void createConsultationCounselorNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConsultationCreateRequest request = createRequest(
                serviceId,
                userId,
                UUID.randomUUID(),
                ConsultationRegistrationStatus.REGISTERED,
                OffsetDateTime.parse("2026-06-30T14:00:00+09:00")
        );
        Service service = Service.builder()
                .id(serviceId)
                .name("PT")
                .active(true)
                .build();

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.createConsultation(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.COUNSELOR_NOT_FOUND);

        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
        verify(userRepository).findByIdAndStore_Id(userId, storeId);
        verifyNoInteractions(inflowPathOptionRepository, customerRepository, consultationRepository);
    }

    @Test
    @DisplayName("상담 등록 시 활성 방문경로 옵션이 없으면 INFLOW_PATH_NOT_FOUND 예외가 발생한다")
    void createConsultationInflowPathNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
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
        ConsultationCreateRequest request = createRequest(
                serviceId,
                userId,
                inflowPathId,
                ConsultationRegistrationStatus.REGISTERED,
                OffsetDateTime.parse("2026-06-30T14:00:00+09:00")
        );

        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId))
                .thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(userId, storeId))
                .thenReturn(Optional.of(counselor));
        when(inflowPathOptionRepository.findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultationService.createConsultation(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.INFLOW_PATH_NOT_FOUND);

        verify(inflowPathOptionRepository).findByIdAndStoreIdAndActiveTrue(inflowPathId, storeId);
        verifyNoInteractions(customerRepository, consultationRepository);
    }

    @Test
    @DisplayName("상담 등록 시 같은 매장에 동일 연락처 고객이 있으면 DUPLICATE_CUSTOMER_PHONE 예외가 발생한다")
    void createConsultationDuplicatePhone() {
        UUID storeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inflowPathId = UUID.randomUUID();
        UUID existingCustomerId = UUID.randomUUID();
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
        Customer existingCustomer = Customer.builder()
                .id(existingCustomerId)
                .store(store)
                .name("기존고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(CustomerStatus.PENDING)
                .firstConsultAt(consultedAt.toLocalDate())
                .latestConsultAt(consultedAt.toLocalDate())
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
                .thenReturn(Optional.of(existingCustomer));

        assertThatThrownBy(() -> consultationService.createConsultation(storeId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ConsultationErrorCode.DUPLICATE_CUSTOMER_PHONE);

        verify(customerRepository).findByPhoneNumAndStoreId("010-1234-5678", storeId);
        verifyNoMoreInteractions(customerRepository);
        verifyNoInteractions(consultationRepository, customerActivityTimelineRepository, interestServiceRepository);
    }

    @Test
    @DisplayName("NO_SHOW is rejected from consultation registration status request values")
    void createConsultationRejectsNoShowRegistrationStatus() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> objectMapper.readValue(
                """
                {
                  "registrationStatus": "NO_SHOW"
                }
                """,
                ConsultationCreateRequest.ConsultationInfo.class
        ))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("NO_SHOW");
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

    private AiCheckPreviewSnapshotRequest aiCheckPreviewSnapshot() {
        AiCheckPreviewItemRequest exerciseGoal = aiCheckPreviewItem(
                AiCheckSignalKey.EXERCISE_GOAL,
                "운동 목적",
                true,
                "체중 감량"
        );
        AiCheckPreviewItemRequest injuryHistory = aiCheckPreviewItem(
                AiCheckSignalKey.INJURY_HISTORY,
                "부상 경험",
                false,
                "아직 확인되지 않음"
        );

        AiCheckPreviewSnapshotRequest request = new AiCheckPreviewSnapshotRequest();
        ReflectionTestUtils.setField(request, "confirmedCount", 1);
        ReflectionTestUtils.setField(request, "totalCount", 2);
        ReflectionTestUtils.setField(request, "items", List.of(exerciseGoal, injuryHistory));
        return request;
    }

    private AiCheckPreviewItemRequest aiCheckPreviewItem(
            AiCheckSignalKey key,
            String label,
            boolean confirmed,
            String value
    ) {
        AiCheckPreviewItemRequest request = new AiCheckPreviewItemRequest();
        ReflectionTestUtils.setField(request, "key", key);
        ReflectionTestUtils.setField(request, "label", label);
        ReflectionTestUtils.setField(request, "confirmed", confirmed);
        ReflectionTestUtils.setField(request, "value", value);
        return request;
    }
}
