package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.dto.response.ReconsultationCreateResponse;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.entity.MessageTemplate;
import com.fitback.domain.customer.entity.NonConversionReason;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerAiInsightRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.FollowUpAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.MessageTemplateRepository;
import com.fitback.domain.customer.repository.NonConversionReasonRepository;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.service.repository.ServiceRepository;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private CustomerAiInsightRepository customerAiInsightRepository;

    @Mock
    private NonConversionReasonRepository nonConversionReasonRepository;

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private FollowUpAiInsightRepository followUpAiInsightRepository;

    @Mock
    private MessageTemplateRepository messageTemplateRepository;

    @Mock
    private CustomerActivityTimelineRepository customerActivityTimelineRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private AiConsultationClient aiConsultationClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository,
                serviceRepository,
                aiConsultationClient,
                userRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("상담내용 상세 조회는 고객, 최신 상담, AI 분석, 후속 연락, 메시지, 타임라인을 조립해 반환한다")
    void getCustomerDetail() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Service registeredService = service(UUID.randomUUID(), store, "정규 PT");
        Service consultedService = service(UUID.randomUUID(), store, "PT");
        InflowPathOption inflowPathOption = inflowPathOption(UUID.randomUUID(), store);
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, registeredService, inflowPathOption);
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                consultedService,
                2,
                AiAnalysisStatus.COMPLETED
        );
        CustomerAiInsight aiInsight = CustomerAiInsight.builder()
                .customer(customer)
                .leadTemperature("WARM")
                .temperatureBasis("가격 부담은 있으나 등록 의향이 남아 있습니다.")
                .priorityScore(78)
                .analyzedAt(OffsetDateTime.parse("2026-07-01T13:00:10+09:00"))
                .build();
        NonConversionReason reason = NonConversionReason.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .reasonType("PRICE_BURDEN")
                .role("PRIMARY")
                .reasonBasis("가격이 부담된다고 언급했습니다.")
                .confidence("HIGH")
                .build();
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .memo("부담 적은 시작 옵션을 안내")
                .build();
        FollowUpAiInsight followUpAiInsight = FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(Map.of("main", "초기 비용 부담 완화"))
                .cautionNote("무리한 할인 압박은 피합니다.")
                .actionBasis(Map.of(
                        "title", "부담 적은 시작 옵션 제안",
                        "description", "큰 패키지보다 시작 부담이 낮은 옵션을 안내합니다.",
                        "reason", "가격 부담이 주요 이탈 요인으로 판단됨"
                ))
                .build();
        MessageTemplate messageTemplate = messageTemplate(
                UUID.randomUUID(),
                customer,
                followUp,
                OffsetDateTime.parse("2026-07-01T13:02:00+09:00")
        );
        CustomerActivityTimeline timeline = timeline(UUID.randomUUID(), store, customer, counselor, latestConsultation.getId());

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.of(aiInsight));
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of(reason));
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));
        when(followUpAiInsightRepository.findById(followUp.getId())).thenReturn(Optional.of(followUpAiInsight));
        when(messageTemplateRepository.findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(customerId, followUp.getId()))
                .thenReturn(Optional.of(messageTemplate));
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of(timeline));

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getCustomer().getCustomerId()).isEqualTo(customerId);
        assertThat(response.getCustomer().getRegisteredServiceId()).isEqualTo(registeredService.getId());
        assertThat(response.getCustomer().getRegisteredServiceName()).isEqualTo("정규 PT");
        assertThat(response.getCustomer().getInflowPathId()).isEqualTo(inflowPathOption.getId());
        assertThat(response.getLatestConsultation().getConsultationId()).isEqualTo(latestConsultation.getId());
        assertThat(response.getLatestConsultation().getSessionNo()).isEqualTo(2);
        assertThat(response.getLatestConsultation().getConsultedServiceName()).isEqualTo("PT");
        assertThat(response.getLatestConsultation().getCounselorName()).isEqualTo("문형주");
        assertThat(response.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(response.getAiInsight().getLeadTemperature()).isEqualTo("WARM");
        assertThat(response.getNonConversionReasons()).hasSize(1);
        assertThat(response.getNonConversionReasons().get(0).getReasonType()).isEqualTo("PRICE_BURDEN");
        assertThat(response.getActiveFollowUp().getFollowUpId()).isEqualTo(followUp.getId());
        assertThat(response.getNextBestAction().getTitle()).isEqualTo("부담 적은 시작 옵션 제안");
        assertThat(response.getNextBestAction().getDescription()).isEqualTo("큰 패키지보다 시작 부담이 낮은 옵션을 안내합니다.");
        assertThat(response.getNextBestAction().getPersuasionPoint()).containsEntry("main", "초기 비용 부담 완화");
        assertThat(response.getLatestMessageTemplate().getMessageTemplateId()).isEqualTo(messageTemplate.getId());
        assertThat(response.getTimeline()).hasSize(1);
        assertThat(response.getTimeline().get(0).getActivityType()).isEqualTo(CustomerActivityType.CONSULTATION_CREATED);

        verify(consultationRepository).findFirstByCustomerIdOrderBySessionNoDesc(customerId);
        verify(messageTemplateRepository).findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(customerId, followUp.getId());
        verify(customerActivityTimelineRepository).findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId);
    }

    @Test
    @DisplayName("상담내용 상세 조회 시 매장이 없으면 STORE_NOT_ASSIGNED 예외가 발생한다")
    void getCustomerDetailStoreNotAssigned() {
        assertThatThrownBy(() -> customerService.getCustomerDetail(null, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.STORE_NOT_ASSIGNED);

        verifyNoInteractions(
                customerRepository,
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository
        );
    }

    @Test
    @DisplayName("상담내용 상세 조회 시 고객이 없으면 CUSTOMER_NOT_FOUND 예외가 발생한다")
    void getCustomerDetailCustomerNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerDetail(storeId, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_NOT_FOUND);

        verify(customerRepository).findById(customerId);
        verifyNoInteractions(
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository
        );
    }

    @Test
    @DisplayName("상담내용 상세 조회 시 고객이 다른 매장 소속이면 CUSTOMER_ACCESS_DENIED 예외가 발생한다")
    void getCustomerDetailCustomerAccessDenied() {
        UUID storeId = UUID.randomUUID();
        UUID otherStoreId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Customer customer = customer(customerId, store(otherStoreId), null, inflowPathOption(UUID.randomUUID(), store(otherStoreId)));

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.getCustomerDetail(storeId, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);

        verify(customerRepository).findById(customerId);
        verifyNoInteractions(
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository
        );
    }

    @Test
    @DisplayName("AI 분석이 PROCESSING이면 AI 관련 상세 데이터가 없을 때 null과 빈 목록을 반환한다")
    void getCustomerDetailProcessingReturnsNullAndEmptyValues() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "상담자"),
                service(UUID.randomUUID(), store, "PT"),
                1,
                AiAnalysisStatus.PROCESSING
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(consultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.empty());
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);
        assertThat(response.getLatestConsultation().getSummary()).isNull();
        assertThat(response.getAiInsight()).isNull();
        assertThat(response.getNonConversionReasons()).isEmpty();
        assertThat(response.getActiveFollowUp()).isNull();
        assertThat(response.getNextBestAction()).isNull();
        assertThat(response.getLatestMessageTemplate()).isNull();
        assertThat(response.getTimeline()).isEmpty();
        verifyNoInteractions(followUpAiInsightRepository, messageTemplateRepository);
    }

    @Test
    @DisplayName("AI 분석이 FAILED이면 실패 상태와 빈 AI 결과를 반환한다")
    void getCustomerDetailFailedReturnsFailedStatusAndEmptyAiValues() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "상담자"),
                service(UUID.randomUUID(), store, "PT"),
                1,
                AiAnalysisStatus.FAILED
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(consultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.empty());
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        assertThat(response.getLatestConsultation().getSummary()).isNull();
        assertThat(response.getAiInsight()).isNull();
        assertThat(response.getNonConversionReasons()).isEmpty();
        assertThat(response.getActiveFollowUp()).isNull();
        assertThat(response.getNextBestAction()).isNull();
        assertThat(response.getLatestMessageTemplate()).isNull();
        verifyNoInteractions(followUpAiInsightRepository, messageTemplateRepository);
    }

    @Test
    @DisplayName("active follow-up이 없으면 다음 최적 액션과 최신 메시지 초안을 조회하지 않고 null로 반환한다")
    void getCustomerDetailWithoutActiveFollowUp() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "상담자"),
                service(UUID.randomUUID(), store, "PT"),
                1,
                AiAnalysisStatus.COMPLETED
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(consultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.empty());
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getActiveFollowUp()).isNull();
        assertThat(response.getNextBestAction()).isNull();
        assertThat(response.getLatestMessageTemplate()).isNull();
        verifyNoInteractions(followUpAiInsightRepository, messageTemplateRepository);
    }

    @Test
    @DisplayName("최신 상담은 sessionNo 내림차순 Repository 메서드 결과를 사용한다")
    void getCustomerDetailUsesLatestConsultationBySessionNoDesc() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "상담자"),
                service(UUID.randomUUID(), store, "PT"),
                3,
                AiAnalysisStatus.COMPLETED
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.empty());
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getLatestConsultation().getSessionNo()).isEqualTo(3);
        verify(consultationRepository).findFirstByCustomerIdOrderBySessionNoDesc(customerId);
    }

    @Test
    @DisplayName("최신 메시지 초안은 generatedAt 내림차순 Repository 메서드 결과를 사용한다")
    void getCustomerDetailUsesLatestMessageTemplateByGeneratedAtDesc() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "상담자"),
                service(UUID.randomUUID(), store, "PT"),
                1,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build();
        OffsetDateTime latestGeneratedAt = OffsetDateTime.parse("2026-07-01T13:05:00+09:00");
        MessageTemplate latestMessageTemplate = messageTemplate(UUID.randomUUID(), customer, followUp, latestGeneratedAt);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(consultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));
        when(followUpAiInsightRepository.findById(followUp.getId())).thenReturn(Optional.empty());
        when(messageTemplateRepository.findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(customerId, followUp.getId()))
                .thenReturn(Optional.of(latestMessageTemplate));
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getLatestMessageTemplate().getGeneratedAt()).isEqualTo(latestGeneratedAt);
        verify(messageTemplateRepository).findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(customerId, followUp.getId());
    }

    @Test
    @DisplayName("재상담 AI 중간분석은 고객과 서비스 검증 후 AI 결과를 저장 없이 반환한다")
    void checkReconsultationPreview() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Service service = service(serviceId, store, "PT");
        ReconsultationCheckPreviewRequest request = reconsultationCheckPreviewRequest(
                serviceId,
                "기존 고객 재상담 내용입니다."
        );
        Map<String, Object> aiResponse = Map.of("overallStatus", "SATISFIED");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId)).thenReturn(Optional.of(service));
        when(aiConsultationClient.checkPreview(argThat(aiRequest ->
                "기존 고객 재상담 내용입니다.".equals(aiRequest.getRawText())
                        && "PT".equals(aiRequest.getServiceName())
                        && "김민지".equals(aiRequest.getCustomerInfo().getName())
                        && Gender.FEMALE == aiRequest.getCustomerInfo().getGender()
                        && LocalDate.of(2001, 5, 10).equals(aiRequest.getCustomerInfo().getBirthDate())
        ))).thenReturn(aiResponse);

        Map<String, Object> response = customerService.checkReconsultationPreview(storeId, customerId, request);

        assertThat(response).isEqualTo(aiResponse);
        verify(customerRepository).findById(customerId);
        verify(serviceRepository).findByIdAndStoreIdAndActiveTrue(serviceId, storeId);
    }

    @Test
    @DisplayName("재상담 AI 중간분석 시 고객이 다른 매장 소속이면 CUSTOMER_ACCESS_DENIED 예외가 발생한다")
    void checkReconsultationPreviewCustomerAccessDenied() {
        UUID storeId = UUID.randomUUID();
        UUID otherStoreId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Store otherStore = store(otherStoreId);
        Customer customer = customer(customerId, otherStore, null, inflowPathOption(UUID.randomUUID(), otherStore));
        ReconsultationCheckPreviewRequest request = reconsultationCheckPreviewRequest(serviceId, "재상담 내용");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.checkReconsultationPreview(storeId, customerId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);

        verify(customerRepository).findById(customerId);
        verifyNoInteractions(serviceRepository, aiConsultationClient);
    }

    @Test
    @DisplayName("재상담 등록은 다음 회차 상담을 저장하고 타임라인 기록 후 AI 분석 이벤트를 발행한다")
    void createReconsultation() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        UUID latestConsultationId = UUID.randomUUID();
        UUID newConsultationId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(serviceId, store, "PT");
        InflowPathOption inflowPathOption = inflowPathOption(UUID.randomUUID(), store);
        Customer customer = customer(customerId, store, null, inflowPathOption);
        User counselor = user(counselorId, store, "문형주");
        Consultation latestConsultation = consultation(
                latestConsultationId,
                customer,
                counselor,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        ReconsultationCreateRequest request = reconsultationCreateRequest(
                serviceId,
                counselorId,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00"),
                "재상담 원문"
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId)).thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(counselorId, storeId)).thenReturn(Optional.of(counselor));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> {
            Consultation unsaved = invocation.getArgument(0);
            return Consultation.builder()
                    .id(newConsultationId)
                    .customer(unsaved.getCustomer())
                    .user(unsaved.getUser())
                    .consultedService(unsaved.getConsultedService())
                    .consultedAt(unsaved.getConsultedAt())
                    .sessionNo(unsaved.getSessionNo())
                    .stage(unsaved.getStage())
                    .sourceType(unsaved.getSourceType())
                    .rawText(unsaved.getRawText())
                    .aiAnalysisStatus(unsaved.getAiAnalysisStatus())
                    .build();
        });

        ReconsultationCreateResponse response = customerService.createReconsultation(storeId, customerId, request);

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getConsultationId()).isEqualTo(newConsultationId);
        assertThat(response.getSessionNo()).isEqualTo(3);
        assertThat(response.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);
        assertThat(customer.getLatestConsultAt()).isEqualTo(LocalDate.of(2026, 7, 8));
        verify(consultationRepository).save(argThat(consultation ->
                consultation.getCustomer() == customer
                        && consultation.getUser() == counselor
                        && consultation.getConsultedService() == service
                        && consultation.getSessionNo() == 3
                        && consultation.getStage() == ConsultationStage.CONSULTATION
                        && consultation.getSourceType() == ConsultationSourceType.DIRECT
                        && "재상담 원문".equals(consultation.getRawText())
                        && consultation.getAiAnalysisStatus() == AiAnalysisStatus.PROCESSING
        ));
        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getCustomer() == customer
                        && timeline.getActorUser() == counselor
                        && timeline.getActivityType() == CustomerActivityType.RECONSULTATION_CREATED
                        && timeline.getRelatedType() == ActivityRelatedType.CONSULTATION
                        && newConsultationId.equals(timeline.getRelatedId())
        ));
        verify(eventPublisher).publishEvent(new ConsultationCreatedEvent(newConsultationId));
    }

    private Store store(UUID storeId) {
        return Store.builder()
                .id(storeId)
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
    }

    private Service service(UUID serviceId, Store store, String name) {
        return Service.builder()
                .id(serviceId)
                .store(store)
                .name(name)
                .active(true)
                .build();
    }

    private User user(UUID userId, Store store, String nickname) {
        return User.builder()
                .id(userId)
                .store(store)
                .email(userId + "@fitback.test")
                .nickname(nickname)
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();
    }

    private InflowPathOption inflowPathOption(UUID inflowPathId, Store store) {
        return InflowPathOption.builder()
                .id(inflowPathId)
                .store(store)
                .name("네이버 예약")
                .displayOrder(1)
                .active(true)
                .build();
    }

    private Customer customer(
            UUID customerId,
            Store store,
            Service registeredService,
            InflowPathOption inflowPathOption
    ) {
        return Customer.builder()
                .id(customerId)
                .store(store)
                .registeredService(registeredService)
                .name("김민지")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(2001, 5, 10))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption)
                .status(CustomerStatus.PENDING)
                .firstConsultAt(LocalDate.of(2026, 7, 1))
                .latestConsultAt(LocalDate.of(2026, 7, 1))
                .build();
    }

    private Consultation consultation(
            UUID consultationId,
            Customer customer,
            User counselor,
            Service service,
            int sessionNo,
            AiAnalysisStatus aiAnalysisStatus
    ) {
        return Consultation.builder()
                .id(consultationId)
                .customer(customer)
                .user(counselor)
                .consultedService(service)
                .consultedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .sessionNo(sessionNo)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText("상담 원문")
                .summary(aiAnalysisStatus == AiAnalysisStatus.COMPLETED ? "가격 부담은 있으나 운동 의지가 있는 고객입니다." : null)
                .aiAnalysisStatus(aiAnalysisStatus)
                .build();
    }

    private MessageTemplate messageTemplate(
            UUID messageTemplateId,
            Customer customer,
            FollowUp followUp,
            OffsetDateTime generatedAt
    ) {
        return MessageTemplate.builder()
                .id(messageTemplateId)
                .customer(customer)
                .followUp(followUp)
                .content("안녕하세요 김민지님...")
                .tonePreset("FRIENDLY")
                .versionType("STANDARD")
                .deliveryStatus("DRAFT")
                .scheduledAt(generatedAt)
                .generatedAt(generatedAt)
                .updatedAt(generatedAt)
                .build();
    }

    private CustomerActivityTimeline timeline(
            UUID timelineId,
            Store store,
            Customer customer,
            User actorUser,
            UUID consultationId
    ) {
        return CustomerActivityTimeline.builder()
                .id(timelineId)
                .store(store)
                .customer(customer)
                .actorUser(actorUser)
                .activityType(CustomerActivityType.CONSULTATION_CREATED)
                .title("상담 기록 등록")
                .description("고객의 상담 기록이 등록되었습니다.")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(consultationId)
                .occurredAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .build();
    }

    private ReconsultationCheckPreviewRequest reconsultationCheckPreviewRequest(UUID serviceId, String rawText) {
        ReconsultationCheckPreviewRequest request = new ReconsultationCheckPreviewRequest();
        ReconsultationCheckPreviewRequest.ConsultationInfo consultation =
                new ReconsultationCheckPreviewRequest.ConsultationInfo();
        ReflectionTestUtils.setField(consultation, "consultedServiceId", serviceId);
        ReflectionTestUtils.setField(consultation, "rawText", rawText);
        ReflectionTestUtils.setField(request, "consultation", consultation);
        return request;
    }

    private ReconsultationCreateRequest reconsultationCreateRequest(
            UUID serviceId,
            UUID counselorId,
            OffsetDateTime consultedAt,
            String rawText
    ) {
        ReconsultationCreateRequest request = new ReconsultationCreateRequest();
        ReconsultationCreateRequest.ConsultationInfo consultation =
                new ReconsultationCreateRequest.ConsultationInfo();
        ReflectionTestUtils.setField(consultation, "consultedServiceId", serviceId);
        ReflectionTestUtils.setField(consultation, "consultedAt", consultedAt);
        ReflectionTestUtils.setField(consultation, "userId", counselorId);
        ReflectionTestUtils.setField(consultation, "rawText", rawText);
        ReflectionTestUtils.setField(request, "consultation", consultation);
        return request;
    }
}
