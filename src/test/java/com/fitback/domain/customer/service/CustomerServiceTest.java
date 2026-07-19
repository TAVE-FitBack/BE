package com.fitback.domain.customer.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.ConsultationMaterialFileData;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewItemRequest;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.dto.request.AiNextActionRegenerateRequest;
import com.fitback.domain.consultation.dto.response.AiNextActionRegenerateResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationMaterial;
import com.fitback.domain.consultation.entity.ConsultationSignal;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.domain.consultation.enums.ConsultationMaterialType;
import com.fitback.domain.consultation.enums.ConsultationRegistrationStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.event.ConsultationCreatedEvent;
import com.fitback.domain.consultation.repository.ConsultationMaterialRepository;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.consultation.service.ConsultationMaterialFileService;
import com.fitback.domain.consultation.service.ConsultationSignalService;
import com.fitback.domain.customer.client.AiMessageClient;
import com.fitback.domain.customer.dto.request.AiMessageGenerateRequest;
import com.fitback.domain.customer.dto.request.CustomerAiAnalysisUpdateRequest;
import com.fitback.domain.customer.dto.request.CustomerStatusUpdateRequest;
import com.fitback.domain.customer.dto.request.FollowUpReplyUpdateRequest;
import com.fitback.domain.customer.dto.request.MessageTemplateCreateRequest;
import com.fitback.domain.customer.dto.request.MessageTemplateMarkSentRequest;
import com.fitback.domain.customer.dto.request.NextActionRegenerateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.response.CustomerAiAnalysisUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerStatusUpdateResponse;
import com.fitback.domain.customer.dto.response.AiMessageGenerateResponse;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.dto.response.FollowUpReplyUpdateResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateCreateResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateMarkSentResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateOptionsResponse;
import com.fitback.domain.customer.dto.response.NextActionRegenerateResponse;
import com.fitback.domain.customer.dto.response.ReconsultationCreateResponse;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.store.entity.InflowPathOption;
import com.fitback.domain.customer.entity.MessageTemplate;
import com.fitback.domain.customer.entity.NonConversionReason;
import com.fitback.domain.customer.enums.ActivityRelatedType;
import com.fitback.domain.customer.enums.ConversionSource;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.MessageTonePreset;
import com.fitback.domain.customer.enums.MessageVersionType;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerAiInsightRepository;
import com.fitback.domain.customer.repository.CustomerRepository;
import com.fitback.domain.customer.repository.EventQueryRepository;
import com.fitback.domain.customer.repository.FollowUpAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.MessageTemplateRepository;
import com.fitback.domain.customer.repository.NonConversionReasonRepository;
import com.fitback.domain.inquiry.repository.InquiryRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
    private ConsultationMaterialRepository consultationMaterialRepository;

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
    private InquiryRepository inquiryRepository;

    @Mock
    private EventQueryRepository eventQueryRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private AiConsultationClient aiConsultationClient;

    @Mock
    private AiMessageClient aiMessageClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FollowUpConversionService followUpConversionService;

    @Mock
    private ConsultationMaterialFileService consultationMaterialFileService;

    @Mock
    private ConsultationSignalService consultationSignalService;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                consultationRepository,
                consultationMaterialRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository,
                inquiryRepository,
                eventQueryRepository,
                serviceRepository,
                aiConsultationClient,
                aiMessageClient,
                userRepository,
                eventPublisher,
                followUpConversionService,
                consultationMaterialFileService,
                consultationSignalService
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
        ConsultationSignal exerciseGoal = consultationSignal(
                latestConsultation,
                AiCheckSignalKey.EXERCISE_GOAL,
                "운동 목적",
                true,
                "체중 감량"
        );
        ConsultationSignal injuryHistory = consultationSignal(
                latestConsultation,
                AiCheckSignalKey.INJURY_HISTORY,
                "부상 경험",
                false,
                "아직 확인되지 않음"
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(consultationSignalService.findCustomerDetailCardSignals(latestConsultation.getId()))
                .thenReturn(List.of(exerciseGoal, injuryHistory));
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
        when(consultationRepository.findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(
                argThat(ids -> ids.contains(latestConsultation.getId())),
                eq(customerId),
                eq(storeId)
        )).thenReturn(List.of(latestConsultation));

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
        assertThat(response.getConsultationSignalSnapshot().getConsultationId()).isEqualTo(latestConsultation.getId());
        assertThat(response.getConsultationSignalSnapshot().getItems())
                .extracting(CustomerDetailResponse.ConsultationSignalItem::getKey)
                .containsExactly(AiCheckSignalKey.EXERCISE_GOAL, AiCheckSignalKey.INJURY_HISTORY);
        assertThat(response.getConsultationSignalSnapshot().getItems().get(0).getLabel()).isEqualTo("운동 목적");
        assertThat(response.getConsultationSignalSnapshot().getItems().get(0).getValue()).isEqualTo("체중 감량");
        assertThat(response.getConsultationSignalSnapshot().getItems().get(1).getConfirmed()).isFalse();
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
        assertThat(response.getTimeline().get(0).getDetail())
                .containsEntry("type", "CONSULTATION")
                .containsEntry("body", latestConsultation.getRawText());

        verify(consultationRepository).findFirstByCustomerIdOrderBySessionNoDesc(customerId);
        verify(consultationSignalService).findCustomerDetailCardSignals(latestConsultation.getId());
        verify(messageTemplateRepository).findFirstByCustomerIdAndFollowUpIdOrderByGeneratedAtDesc(customerId, followUp.getId());
        verify(customerActivityTimelineRepository).findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId);
    }

    @Test
    @DisplayName("customer detail timeline consultation detail contains raw text")
    void getCustomerDetailTimelineConsultationDetailContainsRawText() {
        TimelineDetailContext context = prepareTimelineDetailContext();
        CustomerActivityTimeline timeline = timeline(
                UUID.randomUUID(),
                context.store(),
                context.customer(),
                context.counselor(),
                context.consultation().getId()
        );
        stubCustomerDetailBase(context, List.of(timeline));
        when(consultationRepository.findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(
                argThat(ids -> ids.contains(context.consultation().getId())),
                eq(context.customer().getId()),
                eq(context.store().getId())
        )).thenReturn(List.of(context.consultation()));

        CustomerDetailResponse response = customerService.getCustomerDetail(
                context.store().getId(),
                context.customer().getId()
        );

        CustomerDetailResponse.TimelineItem item = response.getTimeline().get(0);
        assertThat(item.getSummary()).isEqualTo(context.consultation().getRawText());
        assertThat(item.getDetail())
                .containsEntry("type", "CONSULTATION")
                .containsEntry("body", context.consultation().getRawText())
                .containsEntry("summary", context.consultation().getSummary());
    }

    @Test
    @DisplayName("customer detail timeline message detail contains content")
    void getCustomerDetailTimelineMessageTemplateDetailContainsContent() {
        TimelineDetailContext context = prepareTimelineDetailContext();
        MessageTemplate messageTemplate = messageTemplate(
                UUID.randomUUID(),
                context.customer(),
                followUp(context),
                OffsetDateTime.parse("2026-07-01T13:02:00+09:00")
        );
        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .id(UUID.randomUUID())
                .store(context.store())
                .customer(context.customer())
                .actorUser(context.counselor())
                .activityType(CustomerActivityType.MESSAGE_TEMPLATE_CREATED)
                .title("Message template created")
                .description("Message template")
                .relatedType(ActivityRelatedType.MESSAGE_TEMPLATE)
                .relatedId(messageTemplate.getId())
                .afterValue(Map.of("messageTemplateId", messageTemplate.getId()))
                .occurredAt(OffsetDateTime.parse("2026-07-01T13:02:00+09:00"))
                .build();
        stubCustomerDetailBase(context, List.of(timeline));
        when(messageTemplateRepository.findAllByIdInAndCustomerIdAndCustomer_Store_Id(
                argThat(ids -> ids.contains(messageTemplate.getId())),
                eq(context.customer().getId()),
                eq(context.store().getId())
        )).thenReturn(List.of(messageTemplate));

        CustomerDetailResponse response = customerService.getCustomerDetail(
                context.store().getId(),
                context.customer().getId()
        );

        CustomerDetailResponse.TimelineItem item = response.getTimeline().get(0);
        assertThat(item.getSummary()).isEqualTo(messageTemplate.getContent());
        assertThat(item.getDetail())
                .containsEntry("type", "MESSAGE_TEMPLATE")
                .containsEntry("body", messageTemplate.getContent())
                .containsEntry("editable", true);
    }

    @Test
    @DisplayName("customer detail timeline status change contains before and after values")
    void getCustomerDetailTimelineStatusChangeContainsBeforeAndAfterValue() {
        TimelineDetailContext context = prepareTimelineDetailContext();
        Map<String, Object> beforeValue = Map.of("status", CustomerStatus.PENDING);
        Map<String, Object> afterValue = Map.of("status", CustomerStatus.REGISTERED, "followUpAction", "CLOSED");
        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .id(UUID.randomUUID())
                .store(context.store())
                .customer(context.customer())
                .actorUser(context.counselor())
                .activityType(CustomerActivityType.CUSTOMER_STATUS_CHANGED)
                .title("Customer status changed")
                .description("Status changed")
                .relatedType(ActivityRelatedType.CUSTOMER)
                .relatedId(context.customer().getId())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .occurredAt(OffsetDateTime.parse("2026-07-01T14:00:00+09:00"))
                .build();
        stubCustomerDetailBase(context, List.of(timeline));

        CustomerDetailResponse response = customerService.getCustomerDetail(
                context.store().getId(),
                context.customer().getId()
        );

        CustomerDetailResponse.TimelineItem item = response.getTimeline().get(0);
        assertThat(item.getBeforeValue()).isEqualTo(beforeValue);
        assertThat(item.getAfterValue()).isEqualTo(afterValue);
        assertThat(item.getSummary()).isEqualTo("PENDING >> REGISTERED");
        assertThat(item.getDetail())
                .containsEntry("type", "STATUS_CHANGE")
                .containsEntry("beforeStatus", CustomerStatus.PENDING)
                .containsEntry("afterStatus", CustomerStatus.REGISTERED)
                .containsEntry("followUpAction", "CLOSED");
    }

    @Test
    @DisplayName("customer detail timeline AI analysis falls back to after value")
    void getCustomerDetailTimelineAiAnalysisFallback() {
        TimelineDetailContext context = prepareTimelineDetailContext();
        UUID missingConsultationId = UUID.randomUUID();
        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .id(UUID.randomUUID())
                .store(context.store())
                .customer(context.customer())
                .actorUser(context.counselor())
                .activityType(CustomerActivityType.AI_ANALYSIS_COMPLETED)
                .title("AI analysis completed")
                .description("AI analysis")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(missingConsultationId)
                .afterValue(Map.of(
                        "leadTemperature", "WARM",
                        "priorityScore", 78,
                        "primaryReasonType", "PRICE_BURDEN"
                ))
                .occurredAt(OffsetDateTime.parse("2026-07-01T14:00:00+09:00"))
                .build();
        stubCustomerDetailBase(context, List.of(timeline));
        when(consultationRepository.findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(
                argThat(ids -> ids.contains(missingConsultationId)),
                eq(context.customer().getId()),
                eq(context.store().getId())
        )).thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(
                context.store().getId(),
                context.customer().getId()
        );

        CustomerDetailResponse.TimelineItem item = response.getTimeline().get(0);
        assertThat(item.getSummary()).contains("WARM", "78", "PRICE_BURDEN");
        assertThat(item.getDetail())
                .containsEntry("type", "AI_ANALYSIS")
                .containsEntry("consultationId", missingConsultationId)
                .containsEntry("leadTemperature", "WARM")
                .containsEntry("priorityScore", 78)
                .containsEntry("primaryReasonType", "PRICE_BURDEN");
    }

    @Test
    @DisplayName("customer detail timeline returns fallback detail when source is missing")
    void getCustomerDetailTimelineMissingSourceReturnsFallbackDetail() {
        TimelineDetailContext context = prepareTimelineDetailContext();
        UUID missingConsultationId = UUID.randomUUID();
        CustomerActivityTimeline timeline = CustomerActivityTimeline.builder()
                .id(UUID.randomUUID())
                .store(context.store())
                .customer(context.customer())
                .actorUser(context.counselor())
                .activityType(CustomerActivityType.CONSULTATION_CREATED)
                .title("Consultation created")
                .description("Consultation source is missing")
                .relatedType(ActivityRelatedType.CONSULTATION)
                .relatedId(missingConsultationId)
                .occurredAt(OffsetDateTime.parse("2026-07-01T14:00:00+09:00"))
                .build();
        stubCustomerDetailBase(context, List.of(timeline));
        when(consultationRepository.findAllTimelineDetailsByIdsAndCustomerIdAndStoreId(
                argThat(ids -> ids.contains(missingConsultationId)),
                eq(context.customer().getId()),
                eq(context.store().getId())
        )).thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(
                context.store().getId(),
                context.customer().getId()
        );

        CustomerDetailResponse.TimelineItem item = response.getTimeline().get(0);
        assertThat(item.getSummary()).isEqualTo("Consultation source is missing");
        assertThat(item.getDetail())
                .containsEntry("type", "CONSULTATION")
                .containsEntry("heading", "Consultation created")
                .containsEntry("body", "Consultation source is missing")
                .containsEntry("relatedType", ActivityRelatedType.CONSULTATION)
                .containsEntry("relatedId", missingConsultationId);
    }

    @Test
    @DisplayName("상담내용 상세 조회 시 최신 상담의 signal이 없으면 consultationSignalSnapshot items는 빈 배열로 반환한다")
    void getCustomerDetailReturnsEmptyConsultationSignalSnapshotWhenSignalDoesNotExist() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Service consultedService = service(UUID.randomUUID(), store, "PT");
        InflowPathOption inflowPathOption = inflowPathOption(UUID.randomUUID(), store);
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption);
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                consultedService,
                1,
                AiAnalysisStatus.PROCESSING
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(consultationSignalService.findCustomerDetailCardSignals(latestConsultation.getId()))
                .thenReturn(List.of());
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.empty());
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(List.of());

        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);

        assertThat(response.getConsultationSignalSnapshot()).isNotNull();
        assertThat(response.getConsultationSignalSnapshot().getConsultationId()).isEqualTo(latestConsultation.getId());
        assertThat(response.getConsultationSignalSnapshot().getItems()).isEmpty();
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
    @DisplayName("메시지 생성 옵션 조회는 말투, 길이 버전, 매장의 활성 이벤트 목록을 반환한다")
    void getMessageTemplateOptions() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        EventQueryRepository.EventOptionRow event = new EventQueryRepository.EventOptionRow(
                eventId,
                "7월 PT 등록 이벤트",
                "DISCOUNT",
                "PT 첫 달 10% 할인",
                BigDecimal.valueOf(10.0),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(eventQueryRepository.findActiveEventsByStoreId(storeId)).thenReturn(List.of(event));

        MessageTemplateOptionsResponse response = customerService.getMessageTemplateOptions(storeId, customerId);

        assertThat(response.getTonePresets())
                .extracting(MessageTemplateOptionsResponse.TonePresetOption::getTonePreset)
                .containsExactly(MessageTonePreset.FRIENDLY, MessageTonePreset.PROFESSIONAL, MessageTonePreset.SOFT);
        assertThat(response.getTonePresets())
                .extracting(MessageTemplateOptionsResponse.TonePresetOption::getLabel)
                .containsExactly("친근한 말투", "전문적인 말투", "부드러운 말투");
        assertThat(response.getVersionTypes())
                .extracting(MessageTemplateOptionsResponse.VersionTypeOption::getVersionType)
                .containsExactly(MessageVersionType.SHORT, MessageVersionType.STANDARD);
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getEventId()).isEqualTo(eventId);
        assertThat(response.getEvents().get(0).getTitle()).isEqualTo("7월 PT 등록 이벤트");
        assertThat(response.getEvents().get(0).getEventType()).isEqualTo("DISCOUNT");
        assertThat(response.getEvents().get(0).getDiscountRate()).isEqualByComparingTo("10.0");

        verify(customerRepository).findById(customerId);
        verify(eventQueryRepository).findActiveEventsByStoreId(storeId);
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
    @DisplayName("메시지 생성 옵션 조회 시 고객이 다른 매장 소속이면 CUSTOMER_ACCESS_DENIED 예외가 발생한다")
    void getMessageTemplateOptionsAccessDenied() {
        UUID storeId = UUID.randomUUID();
        UUID otherStoreId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store otherStore = store(otherStoreId);
        Customer customer = customer(customerId, otherStore, null, inflowPathOption(UUID.randomUUID(), otherStore));

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.getMessageTemplateOptions(storeId, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_ACCESS_DENIED);

        verify(customerRepository).findById(customerId);
        verifyNoInteractions(eventQueryRepository);
    }

    @Test
    @DisplayName("답장 있음으로 변경하면 hasReply를 true로 저장하고 repliedAt을 현재 시각으로 저장한다")
    void updateFollowUpReplyTrue() {
        UUID storeId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "문형주"),
                service(UUID.randomUUID(), store, "PT"),
                1,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(followUpId)
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        FollowUpReplyUpdateRequest request = followUpReplyUpdateRequest(true);

        when(followUpRepository.findByIdAndCustomer_Store_Id(followUpId, storeId))
                .thenReturn(Optional.of(followUp));

        FollowUpReplyUpdateResponse response = customerService.updateFollowUpReply(storeId, followUpId, request);

        assertThat(response.getFollowUpId()).isEqualTo(followUpId);
        assertThat(response.isHasReply()).isTrue();
        assertThat(response.getRepliedAt()).isNotNull();
        assertThat(followUp.isHasReply()).isTrue();
        assertThat(followUp.getRepliedAt()).isNotNull();
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.PENDING);
        assertThat(followUp.getContactRound()).isEqualTo(2);
        verify(followUpRepository).findByIdAndCustomer_Store_Id(followUpId, storeId);
        verifyNoInteractions(
                customerRepository,
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository,
                aiConsultationClient,
                aiMessageClient
        );
    }

    @Test
    @DisplayName("답장 없음으로 변경하면 hasReply를 false로 저장하고 repliedAt을 null로 저장한다")
    void updateFollowUpReplyFalse() {
        UUID storeId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                user(UUID.randomUUID(), store, "문형주"),
                service(UUID.randomUUID(), store, "PT"),
                1,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(followUpId)
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.COMPLETED)
                .contactRound(3)
                .hasReply(true)
                .repliedAt(OffsetDateTime.parse("2026-07-10T15:30:00+09:00"))
                .build();
        FollowUpReplyUpdateRequest request = followUpReplyUpdateRequest(false);

        when(followUpRepository.findByIdAndCustomer_Store_Id(followUpId, storeId))
                .thenReturn(Optional.of(followUp));

        FollowUpReplyUpdateResponse response = customerService.updateFollowUpReply(storeId, followUpId, request);

        assertThat(response.getFollowUpId()).isEqualTo(followUpId);
        assertThat(response.isHasReply()).isFalse();
        assertThat(response.getRepliedAt()).isNull();
        assertThat(followUp.isHasReply()).isFalse();
        assertThat(followUp.getRepliedAt()).isNull();
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
        assertThat(followUp.getContactRound()).isEqualTo(3);
        verify(followUpRepository).findByIdAndCustomer_Store_Id(followUpId, storeId);
        verifyNoInteractions(
                customerRepository,
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository,
                aiConsultationClient,
                aiMessageClient
        );
    }

    @Test
    @DisplayName("다른 매장 follow_up 답장 유무 변경은 FOLLOW_UP_NOT_FOUND 예외가 발생한다")
    void updateFollowUpReplyOtherStoreNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        FollowUpReplyUpdateRequest request = followUpReplyUpdateRequest(true);

        when(followUpRepository.findByIdAndCustomer_Store_Id(followUpId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateFollowUpReply(storeId, followUpId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.FOLLOW_UP_NOT_FOUND);

        verify(followUpRepository).findByIdAndCustomer_Store_Id(followUpId, storeId);
        verifyNoInteractions(
                customerRepository,
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpAiInsightRepository,
                messageTemplateRepository,
                customerActivityTimelineRepository,
                aiConsultationClient,
                aiMessageClient
        );
    }

    @Test
    @DisplayName("메시지 초안 생성은 PENDING follow_up 기준으로 AI 메시지를 생성하고 DRAFT 상태로 저장한다")
    void createMessageTemplate() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                actorUser,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(followUpId)
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .memo("부담 적은 시작 옵션 안내")
                .build();
        FollowUpAiInsight followUpAiInsight = FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(Map.of("main", "초기 비용 부담 완화"))
                .cautionNote("무리한 할인 압박은 피합니다.")
                .actionBasis(Map.of(
                        "title", "부담 적은 단기권 옵션 안내",
                        "description", "큰 패키지보다 시작 부담이 낮은 단기권을 안내합니다."
                ))
                .build();
        CustomerAiInsight aiInsight = CustomerAiInsight.builder()
                .customer(customer)
                .leadTemperature("WARM")
                .priorityScore(82)
                .build();
        NonConversionReason reason = NonConversionReason.builder()
                .customer(customer)
                .consultation(latestConsultation)
                .reasonType("PRICE_BURDEN")
                .role("PRIMARY")
                .reasonBasis("가격을 부담스러워했습니다.")
                .build();
        EventQueryRepository.EventOptionRow event = new EventQueryRepository.EventOptionRow(
                eventId,
                "7월 PT 등록 이벤트",
                "DISCOUNT",
                "PT 첫 달 10% 할인",
                BigDecimal.valueOf(10.0),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );
        MessageTemplateCreateRequest request = messageTemplateCreateRequest(
                followUpId,
                MessageTonePreset.FRIENDLY,
                MessageVersionType.STANDARD,
                eventId,
                "처음 시작 부담이 적다는 점을 강조해주세요."
        );
        AiMessageGenerateResponse aiResponse = AiMessageGenerateResponse.builder()
                .content("안녕하세요 김민지님, 이번 달 PT 할인 혜택을 안내드립니다.")
                .tonePreset(MessageTonePreset.FRIENDLY)
                .versionType(MessageVersionType.STANDARD)
                .build();
        MessageTemplate savedMessageTemplate = MessageTemplate.builder()
                .id(messageTemplateId)
                .followUp(followUp)
                .eventId(eventId)
                .customer(customer)
                .content(aiResponse.getContent())
                .versionType(MessageVersionType.STANDARD.name())
                .tonePreset(MessageTonePreset.FRIENDLY.name())
                .deliveryStatus("DRAFT")
                .contactRound(2)
                .generatedAt(OffsetDateTime.parse("2026-07-08T15:00:00+09:00"))
                .updatedAt(OffsetDateTime.parse("2026-07-08T15:00:00+09:00"))
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(followUpAiInsightRepository.findById(followUpId)).thenReturn(Optional.of(followUpAiInsight));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.of(aiInsight));
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of(reason));
        when(eventQueryRepository.findActiveEventByIdAndStoreId(eventId, storeId)).thenReturn(Optional.of(event));
        when(aiMessageClient.generateMessage(any(AiMessageGenerateRequest.class))).thenReturn(aiResponse);
        when(messageTemplateRepository.save(any(MessageTemplate.class))).thenReturn(savedMessageTemplate);

        MessageTemplateCreateResponse response = customerService.createMessageTemplate(
                storeId,
                userId,
                customerId,
                request
        );

        assertThat(response.getMessageTemplateId()).isEqualTo(messageTemplateId);
        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getFollowUpId()).isEqualTo(followUpId);
        assertThat(response.getContent()).isEqualTo(aiResponse.getContent());
        assertThat(response.getTonePreset()).isEqualTo(MessageTonePreset.FRIENDLY);
        assertThat(response.getVersionType()).isEqualTo(MessageVersionType.STANDARD);
        assertThat(response.getDeliveryStatus().name()).isEqualTo("DRAFT");

        verify(aiMessageClient).generateMessage(argThat(aiRequest ->
                customerId.equals(aiRequest.getCustomer().getCustomerId())
                        && "김민지".equals(aiRequest.getCustomer().getName())
                        && latestConsultation.getId().equals(aiRequest.getLatestConsultation().getConsultationId())
                        && "WARM".equals(aiRequest.getAiInsight().getLeadTemperature())
                        && Integer.valueOf(82).equals(aiRequest.getAiInsight().getPriorityScore())
                        && aiRequest.getNonConversionReasons().size() == 1
                        && "PRICE_BURDEN".equals(aiRequest.getNonConversionReasons().get(0).getReasonType())
                        && "부담 적은 단기권 옵션 안내".equals(aiRequest.getNextBestAction().getTitle())
                        && eventId.equals(aiRequest.getEvent().getEventId())
                        && aiRequest.getMessageOptions().getTonePreset() == MessageTonePreset.FRIENDLY
                        && aiRequest.getMessageOptions().getVersionType() == MessageVersionType.STANDARD
        ));
        verify(messageTemplateRepository).save(argThat(messageTemplate ->
                messageTemplate.getCustomer() == customer
                        && messageTemplate.getFollowUp() == followUp
                        && eventId.equals(messageTemplate.getEventId())
                        && Integer.valueOf(2).equals(messageTemplate.getContactRound())
                        && messageTemplate.getScheduledAt() == null
                        && "DRAFT".equals(messageTemplate.getDeliveryStatus())
                        && MessageTonePreset.FRIENDLY.name().equals(messageTemplate.getTonePreset())
                        && MessageVersionType.STANDARD.name().equals(messageTemplate.getVersionType())
        ));
        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getActivityType() == CustomerActivityType.MESSAGE_TEMPLATE_CREATED
                        && timeline.getRelatedType() == ActivityRelatedType.MESSAGE_TEMPLATE
                        && messageTemplateId.equals(timeline.getRelatedId())
                        && actorUser == timeline.getActorUser()
                        && messageTemplateId.equals(timeline.getAfterValue().get("messageTemplateId"))
                        && followUpId.equals(timeline.getAfterValue().get("followUpId"))
        ));
        verify(followUpRepository, never()).save(any(FollowUp.class));
        verify(followUpAiInsightRepository, never()).save(any(FollowUpAiInsight.class));
        verify(customerAiInsightRepository, never()).save(any(CustomerAiInsight.class));
        verify(nonConversionReasonRepository, never()).saveAll(any());
        verify(nonConversionReasonRepository, never()).deleteAllByCustomerId(any());
    }

    @Test
    @DisplayName("메시지 초안 생성 시 follow_up이 없으면 ACTIVE_FOLLOW_UP_NOT_FOUND 예외가 발생한다")
    void createMessageTemplateFollowUpNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        User actorUser = user(userId, store, "문형주");
        MessageTemplateCreateRequest request = messageTemplateCreateRequest(
                followUpId,
                MessageTonePreset.FRIENDLY,
                MessageVersionType.STANDARD,
                null,
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.createMessageTemplate(storeId, userId, customerId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.ACTIVE_FOLLOW_UP_NOT_FOUND);

        verifyNoInteractions(aiMessageClient, messageTemplateRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("메시지 초안 생성 시 follow_up이 PENDING이 아니면 FOLLOW_UP_NOT_PENDING 예외가 발생한다")
    void createMessageTemplateFollowUpNotPending() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Store store = store(storeId);
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        User actorUser = user(userId, store, "문형주");
        FollowUp followUp = FollowUp.builder()
                .id(followUpId)
                .customer(customer)
                .consultation(consultation(UUID.randomUUID(), customer, actorUser, service(UUID.randomUUID(), store, "PT"), 1, AiAnalysisStatus.COMPLETED))
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.COMPLETED)
                .build();
        MessageTemplateCreateRequest request = messageTemplateCreateRequest(
                followUpId,
                MessageTonePreset.FRIENDLY,
                MessageVersionType.STANDARD,
                null,
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));

        assertThatThrownBy(() -> customerService.createMessageTemplate(storeId, userId, customerId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.FOLLOW_UP_NOT_PENDING);

        verifyNoInteractions(aiMessageClient, messageTemplateRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("메시지 초안 생성 시 선택 이벤트가 활성 이벤트가 아니면 EVENT_NOT_FOUND 예외가 발생한다")
    void createMessageTemplateEventNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, actorUser, service, 2, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(followUpId)
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .build();
        FollowUpAiInsight followUpAiInsight = FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(Map.of("main", "초기 비용 부담 완화"))
                .actionBasis(Map.of("title", "액션", "description", "설명"))
                .build();
        MessageTemplateCreateRequest request = messageTemplateCreateRequest(
                followUpId,
                MessageTonePreset.FRIENDLY,
                MessageVersionType.STANDARD,
                eventId,
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId)).thenReturn(Optional.of(latestConsultation));
        when(followUpAiInsightRepository.findById(followUpId)).thenReturn(Optional.of(followUpAiInsight));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of());
        when(eventQueryRepository.findActiveEventByIdAndStoreId(eventId, storeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.createMessageTemplate(storeId, userId, customerId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.EVENT_NOT_FOUND);

        verifyNoInteractions(aiMessageClient, messageTemplateRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("메시지 초안 생성 시 FastAPI가 실패하면 MESSAGE_GENERATION_FAILED 예외가 발생하고 저장하지 않는다")
    void createMessageTemplateAiFailed() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, actorUser, service, 2, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(followUpId)
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .build();
        FollowUpAiInsight followUpAiInsight = FollowUpAiInsight.builder()
                .followUp(followUp)
                .persuasionPoint(Map.of("main", "초기 비용 부담 완화"))
                .actionBasis(Map.of("title", "액션", "description", "설명"))
                .build();
        MessageTemplateCreateRequest request = messageTemplateCreateRequest(
                followUpId,
                MessageTonePreset.FRIENDLY,
                MessageVersionType.STANDARD,
                null,
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));
        when(followUpRepository.findById(followUpId)).thenReturn(Optional.of(followUp));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId)).thenReturn(Optional.of(latestConsultation));
        when(followUpAiInsightRepository.findById(followUpId)).thenReturn(Optional.of(followUpAiInsight));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId)).thenReturn(List.of());
        when(aiMessageClient.generateMessage(any(AiMessageGenerateRequest.class)))
                .thenThrow(new BusinessException(CustomerErrorCode.MESSAGE_GENERATION_FAILED));

        assertThatThrownBy(() -> customerService.createMessageTemplate(storeId, userId, customerId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.MESSAGE_GENERATION_FAILED);

        verify(messageTemplateRepository, never()).save(any(MessageTemplate.class));
        verify(customerActivityTimelineRepository, never()).save(any(CustomerActivityTimeline.class));
    }

    @Test
    @DisplayName("메시지 전송 완료는 메시지를 SENT로 변경하고 연결된 follow_up을 COMPLETED 처리한다")
    void markMessageTemplateSent() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-07-08T15:20:00+09:00");
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                actorUser,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        MessageTemplate messageTemplate = messageTemplate(
                messageTemplateId,
                customer,
                followUp,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00")
        );
        MessageTemplateMarkSentRequest request = messageTemplateMarkSentRequest(sentAt);

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.of(messageTemplate));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));

        MessageTemplateMarkSentResponse response = customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                request
        );

        assertThat(response.getMessageTemplateId()).isEqualTo(messageTemplateId);
        assertThat(response.getDeliveryStatus().name()).isEqualTo("SENT");
        assertThat(response.getSentAt()).isEqualTo(sentAt);
        assertThat(response.getFollowUpId()).isEqualTo(followUp.getId());
        assertThat(response.getFollowUpStatus()).isEqualTo(FollowUpStatus.SENT);
        assertThat(response.getContactRound()).isEqualTo(2);
        assertThat(messageTemplate.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(messageTemplate.getSentAt()).isEqualTo(sentAt);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.SENT);
        assertThat(followUp.getContactRound()).isEqualTo(2);

        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getActivityType() == CustomerActivityType.MESSAGE_SENT
                        && timeline.getRelatedType() == ActivityRelatedType.MESSAGE_TEMPLATE
                        && messageTemplateId.equals(timeline.getRelatedId())
                        && actorUser == timeline.getActorUser()
                        && "DRAFT".equals(timeline.getBeforeValue().get("deliveryStatus"))
                        && "SENT".equals(timeline.getAfterValue().get("deliveryStatus"))
                        && sentAt.equals(timeline.getAfterValue().get("sentAt"))
        ));
        verifyNoInteractions(
                aiMessageClient,
                consultationRepository,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpAiInsightRepository,
                eventQueryRepository
        );
    }

    @Test
    @DisplayName("Third round message sent completes follow-up")
    void markThirdRoundMessageTemplateSentCompletesFollowUp() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-07-08T15:20:00+09:00");
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "ë¬¸í˜•ì£¼");
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                actorUser,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(3)
                .build();
        MessageTemplate messageTemplate = messageTemplate(
                messageTemplateId,
                customer,
                followUp,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00")
        );

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.of(messageTemplate));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));

        MessageTemplateMarkSentResponse response = customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(sentAt)
        );

        assertThat(response.getFollowUpStatus()).isEqualTo(FollowUpStatus.COMPLETED);
        assertThat(response.getContactRound()).isEqualTo(3);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
        assertThat(followUp.getContactRound()).isEqualTo(3);
        assertThat(messageTemplate.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(messageTemplate.getSentAt()).isEqualTo(sentAt);
        verify(customerActivityTimelineRepository).save(any(CustomerActivityTimeline.class));
        verifyNoInteractions(aiConsultationClient, aiMessageClient, followUpRepository);
    }

    @Test
    @DisplayName("Registered customer message sent closes follow-up")
    void markMessageTemplateSentRegisteredCustomerClosesFollowUp() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-07-08T15:20:00+09:00");
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        customer.markRegistered(service, OffsetDateTime.parse("2026-07-08T10:00:00+09:00"));
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, actorUser, service, 2, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(3)
                .build();
        MessageTemplate messageTemplate = messageTemplate(
                messageTemplateId,
                customer,
                followUp,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00")
        );

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.of(messageTemplate));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));

        MessageTemplateMarkSentResponse response = customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(sentAt)
        );

        assertThat(response.getFollowUpStatus()).isEqualTo(FollowUpStatus.CLOSED);
        assertThat(response.getContactRound()).isEqualTo(3);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.CLOSED);
        assertThat(followUp.getContactRound()).isEqualTo(3);
        assertThat(messageTemplate.getDeliveryStatus()).isEqualTo("SENT");
        verify(customerActivityTimelineRepository).save(any(CustomerActivityTimeline.class));
        verifyNoInteractions(aiConsultationClient, aiMessageClient, followUpRepository);
    }

    @Test
    @DisplayName("이탈 고객 메시지 전송 완료는 연결된 follow_up을 CLOSED 처리하고 contactRound를 유지한다")
    void markMessageTemplateSentLostCustomerClosesFollowUp() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        customer.markStatus(CustomerStatus.LOST);
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, actorUser, service, 2, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        MessageTemplate messageTemplate = messageTemplate(
                messageTemplateId,
                customer,
                followUp,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00")
        );

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.of(messageTemplate));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));

        MessageTemplateMarkSentResponse response = customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(OffsetDateTime.parse("2026-07-08T15:20:00+09:00"))
        );

        assertThat(response.getFollowUpStatus()).isEqualTo(FollowUpStatus.CLOSED);
        assertThat(response.getContactRound()).isEqualTo(2);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.CLOSED);
        assertThat(followUp.getContactRound()).isEqualTo(2);
        verifyNoInteractions(aiConsultationClient, aiMessageClient, followUpRepository);
    }

    @Test
    @DisplayName("메시지 전송 완료 시 메시지 초안이 없으면 MESSAGE_TEMPLATE_NOT_FOUND 예외가 발생한다")
    void markMessageTemplateSentMessageNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(OffsetDateTime.parse("2026-07-08T15:20:00+09:00"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.MESSAGE_TEMPLATE_NOT_FOUND);

        verifyNoInteractions(userRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("다른 매장 메시지 전송 완료 접근은 MESSAGE_TEMPLATE_NOT_FOUND 예외가 발생한다")
    void markMessageTemplateSentOtherStoreMessageNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(OffsetDateTime.parse("2026-07-08T15:20:00+09:00"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.MESSAGE_TEMPLATE_NOT_FOUND);

        verify(messageTemplateRepository).findByIdAndCustomer_Store_Id(messageTemplateId, storeId);
        verifyNoInteractions(userRepository, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("메시지 전송 완료 시 연결된 follow_up이 없으면 FOLLOW_UP_NOT_FOUND 예외가 발생한다")
    void markMessageTemplateSentFollowUpNotFound() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        Store store = store(storeId);
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        MessageTemplate messageTemplate = messageTemplate(
                messageTemplateId,
                customer,
                null,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00")
        );

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.of(messageTemplate));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));

        assertThatThrownBy(() -> customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(OffsetDateTime.parse("2026-07-08T15:20:00+09:00"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.FOLLOW_UP_NOT_FOUND);

        assertThat(messageTemplate.getDeliveryStatus()).isEqualTo("DRAFT");
        verify(customerActivityTimelineRepository, never()).save(any(CustomerActivityTimeline.class));
    }

    @Test
    @DisplayName("메시지 전송 완료 시 follow_up이 PENDING이 아니면 FOLLOW_UP_NOT_PENDING 예외가 발생한다")
    void markMessageTemplateSentFollowUpNotPending() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageTemplateId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User actorUser = user(userId, store, "문형주");
        Customer customer = customer(UUID.randomUUID(), store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, actorUser, service, 2, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.COMPLETED)
                .build();
        MessageTemplate messageTemplate = messageTemplate(
                messageTemplateId,
                customer,
                followUp,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00")
        );

        when(messageTemplateRepository.findByIdAndCustomer_Store_Id(messageTemplateId, storeId))
                .thenReturn(Optional.of(messageTemplate));
        when(userRepository.findByIdAndStore_Id(userId, storeId)).thenReturn(Optional.of(actorUser));

        assertThatThrownBy(() -> customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                messageTemplateMarkSentRequest(OffsetDateTime.parse("2026-07-08T15:20:00+09:00"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.FOLLOW_UP_NOT_PENDING);

        assertThat(messageTemplate.getDeliveryStatus()).isEqualTo("DRAFT");
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.COMPLETED);
        verify(customerActivityTimelineRepository, never()).save(any(CustomerActivityTimeline.class));
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
        assertThat(response.getRegistrationStatus()).isEqualTo(ConsultationRegistrationStatus.PENDING);
        assertThat(response.getFollowUpAction()).isEqualTo("KEEP");
        assertThat(response.isFollowUpConversionCreated()).isFalse();
        assertThat(response.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);
        assertThat(customer.getLatestConsultAt()).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.PENDING);
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
        verify(consultationSignalService).saveSnapshot(any(Consultation.class), org.mockito.ArgumentMatchers.isNull());
        verifyNoInteractions(followUpConversionService);
    }

    @Test
    @DisplayName("재상담 등록 요청에 aiCheckPreview가 있으면 consultation 저장 후 consultation_signal 저장 서비스를 호출한다")
    void createReconsultationSavesAiCheckPreviewSnapshot() {
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
        Consultation savedConsultation = Consultation.builder()
                .id(newConsultationId)
                .customer(customer)
                .user(counselor)
                .consultedService(service)
                .consultedAt(OffsetDateTime.parse("2026-07-08T15:00:00+09:00"))
                .sessionNo(3)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText("재상담 원문")
                .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                .build();
        AiCheckPreviewSnapshotRequest aiCheckPreview = aiCheckPreviewSnapshot();
        ReconsultationCreateRequest request = reconsultationCreateRequest(
                serviceId,
                counselorId,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00"),
                "재상담 원문"
        );
        ReflectionTestUtils.setField(request, "aiCheckPreview", aiCheckPreview);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId)).thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(counselorId, storeId)).thenReturn(Optional.of(counselor));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(consultationRepository.save(any(Consultation.class))).thenReturn(savedConsultation);

        customerService.createReconsultation(storeId, customerId, request);

        InOrder inOrder = inOrder(consultationRepository, consultationSignalService);
        inOrder.verify(consultationRepository).save(any(Consultation.class));
        inOrder.verify(consultationSignalService).saveSnapshot(savedConsultation, aiCheckPreview);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("재상담 등록 시 첨부파일이 있으면 customer_id와 consultation_id 기준으로 상담자료를 저장한 뒤 AI 이벤트를 발행한다")
    void createReconsultationSavesMaterialsBeforePublishingEvent() {
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
        MockMultipartFile file = new MockMultipartFile(
                "materials",
                "reconsultation-note.txt",
                "text/plain",
                "재상담 첨부자료".getBytes()
        );
        List<MultipartFile> materials = List.of(file);
        ConsultationMaterialFileData materialData = ConsultationMaterialFileData.builder()
                .materialType(ConsultationMaterialType.OTHER)
                .title("reconsultation-note")
                .originalFileName("reconsultation-note.txt")
                .contentType("text/plain")
                .fileSize(file.getSize())
                .content("재상담 첨부자료")
                .build();

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
        when(consultationMaterialFileService.extractMaterials(materials))
                .thenReturn(List.of(materialData));

        ReconsultationCreateResponse response = customerService.createReconsultation(
                storeId,
                customerId,
                request,
                materials
        );

        assertThat(response.getConsultationId()).isEqualTo(newConsultationId);
        ArgumentCaptor<List<ConsultationMaterial>> materialCaptor = ArgumentCaptor.forClass(List.class);
        verify(consultationMaterialRepository).saveAll(materialCaptor.capture());
        assertThat(materialCaptor.getValue()).hasSize(1);
        ConsultationMaterial material = materialCaptor.getValue().get(0);
        assertThat(material.getStore()).isEqualTo(store);
        assertThat(material.getCustomer()).isEqualTo(customer);
        assertThat(material.getConsultation().getId()).isEqualTo(newConsultationId);
        assertThat(material.getInquiry()).isNull();
        assertThat(material.getMaterialType()).isEqualTo(ConsultationMaterialType.OTHER);
        assertThat(material.getTitle()).isEqualTo("reconsultation-note");
        assertThat(material.getOriginalFileName()).isEqualTo("reconsultation-note.txt");
        assertThat(material.getContentType()).isEqualTo("text/plain");
        assertThat(material.getFileSize()).isEqualTo(file.getSize());
        assertThat(material.getContent()).isEqualTo("재상담 첨부자료");
        assertThat(material.getCreatedBy()).isEqualTo(counselor);

        ArgumentCaptor<ConsultationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(ConsultationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().consultationId()).isEqualTo(newConsultationId);

        InOrder inOrder = inOrder(consultationMaterialRepository, eventPublisher);
        inOrder.verify(consultationMaterialRepository).saveAll(any());
        inOrder.verify(eventPublisher).publishEvent(any(ConsultationCreatedEvent.class));
    }

    @Test
    @DisplayName("재상담 등록에서 REGISTERED를 선택하면 고객 등록 처리, PENDING follow_up 종료, 전환 귀속 저장을 함께 수행한다")
    void createReconsultationRegisteredClosesFollowUpAndRecordsConversion() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        OffsetDateTime consultedAt = OffsetDateTime.parse("2026-07-08T15:00:00+09:00");
        Store store = store(storeId);
        Service service = service(serviceId, store, "PT");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        User counselor = user(counselorId, store, "문형주");
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, counselor, service, 1, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 9))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        ReconsultationCreateRequest request = reconsultationCreateRequest(
                serviceId,
                counselorId,
                consultedAt,
                "재상담 원문",
                ConsultationRegistrationStatus.REGISTERED,
                serviceId
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId)).thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(counselorId, storeId)).thenReturn(Optional.of(counselor));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId)).thenReturn(Optional.of(latestConsultation));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> {
            Consultation unsaved = invocation.getArgument(0);
            return Consultation.builder()
                    .id(consultationId)
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
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));
        when(followUpConversionService.recordConversionIfAbsent(customer, consultedAt, ConversionSource.RECONSULTATION))
                .thenReturn(true);

        ReconsultationCreateResponse response = customerService.createReconsultation(storeId, customerId, request);

        assertThat(response.getRegistrationStatus()).isEqualTo(ConsultationRegistrationStatus.REGISTERED);
        assertThat(response.getRegisteredServiceId()).isEqualTo(serviceId);
        assertThat(response.getFollowUpAction()).isEqualTo("CLOSED");
        assertThat(response.isFollowUpConversionCreated()).isTrue();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.REGISTERED);
        assertThat(customer.getRegisteredService()).isEqualTo(service);
        assertThat(customer.getRegisteredAt()).isEqualTo(consultedAt);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.CLOSED);
        verify(followUpConversionService).recordConversionIfAbsent(customer, consultedAt, ConversionSource.RECONSULTATION);
    }

    @Test
    @DisplayName("재상담 등록에서 LOST를 선택하면 PENDING follow_up을 CLOSED 처리하고 전환 귀속은 저장하지 않는다")
    void createReconsultationLostClosesFollowUpWithoutConversion() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID counselorId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(serviceId, store, "PT");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        User counselor = user(counselorId, store, "문형주");
        Consultation latestConsultation = consultation(UUID.randomUUID(), customer, counselor, service, 1, AiAnalysisStatus.COMPLETED);
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 9))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        ReconsultationCreateRequest request = reconsultationCreateRequest(
                serviceId,
                counselorId,
                OffsetDateTime.parse("2026-07-08T15:00:00+09:00"),
                "재상담 원문",
                ConsultationRegistrationStatus.LOST,
                null
        );

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(serviceId, storeId)).thenReturn(Optional.of(service));
        when(userRepository.findByIdAndStore_Id(counselorId, storeId)).thenReturn(Optional.of(counselor));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId)).thenReturn(Optional.of(latestConsultation));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));

        ReconsultationCreateResponse response = customerService.createReconsultation(storeId, customerId, request);

        assertThat(response.getRegistrationStatus()).isEqualTo(ConsultationRegistrationStatus.LOST);
        assertThat(response.getFollowUpAction()).isEqualTo("CLOSED");
        assertThat(response.isFollowUpConversionCreated()).isFalse();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.LOST);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.CLOSED);
        verifyNoInteractions(followUpConversionService);
    }

    @Test
    @DisplayName("AI 분석값 수동 수정은 최신 상담 요약과 AI 분석값, 이탈요인만 수정하고 follow_up은 유지한다")
    void updateAiAnalysis() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        CustomerAiInsight aiInsight = CustomerAiInsight.builder()
                .customer(customer)
                .leadTemperature("WARM")
                .temperatureBasis("기존 온도 근거")
                .priorityScore(70)
                .analyzedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .build();
        NonConversionReason existingReason = NonConversionReason.builder()
                .customer(customer)
                .consultation(latestConsultation)
                .reasonType("PRICE_BURDEN")
                .role("PRIMARY")
                .reasonBasis("기존 가격 부담")
                .confidence("HIGH")
                .build();
        CustomerAiAnalysisUpdateRequest request = aiAnalysisUpdateRequest();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.of(aiInsight));
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of(existingReason));

        CustomerAiAnalysisUpdateResponse response = customerService.updateAiAnalysis(storeId, customerId, request);

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.isNextActionRegenerationAvailable()).isTrue();
        assertThat(latestConsultation.getSummary()).isEqualTo("수정된 AI 상담요약");
        assertThat(aiInsight.getLeadTemperature()).isEqualTo("HOT");
        assertThat(aiInsight.getTemperatureBasis()).isEqualTo("수정된 온도 근거");
        assertThat(aiInsight.getPriorityScore()).isEqualTo(70);
        verify(customerAiInsightRepository).save(aiInsight);
        verify(nonConversionReasonRepository).deleteAllByCustomerId(customerId);
        verify(nonConversionReasonRepository).saveAll(argThat(reasons -> {
            List<NonConversionReason> reasonList = StreamSupport.stream(reasons.spliterator(), false).toList();
            return reasonList.size() == 1
                    && "SCHEDULE_CONFLICT".equals(reasonList.get(0).getReasonType())
                    && "PRIMARY".equals(reasonList.get(0).getRole())
                    && latestConsultation == reasonList.get(0).getConsultation();
        }));
        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getActivityType() == CustomerActivityType.AI_ANALYSIS_MANUALLY_UPDATED
                        && timeline.getRelatedType() == ActivityRelatedType.CUSTOMER
                        && customerId.equals(timeline.getRelatedId())
                        && "PRICE_BURDEN".equals(timeline.getBeforeValue().get("primaryReasonType"))
                        && "SCHEDULE_CONFLICT".equals(timeline.getAfterValue().get("primaryReasonType"))
        ));
        verifyNoInteractions(followUpRepository, followUpAiInsightRepository);
    }

    @Test
    @DisplayName("다음 최적 액션 재생성은 최신 분석값으로 AI를 호출하고 기존 PENDING을 대체한 뒤 새 PENDING을 저장한다")
    void regenerateNextAction() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID oldFollowUpId = UUID.randomUUID();
        UUID newFollowUpId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        CustomerAiInsight aiInsight = CustomerAiInsight.builder()
                .customer(customer)
                .leadTemperature("HOT")
                .temperatureBasis("수정된 온도 근거")
                .priorityScore(70)
                .analyzedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .build();
        NonConversionReason reason = NonConversionReason.builder()
                .customer(customer)
                .consultation(latestConsultation)
                .reasonType("SCHEDULE_CONFLICT")
                .role("PRIMARY")
                .reasonBasis("일정 조율이 어렵다고 언급했습니다.")
                .confidence("MEDIUM")
                .build();
        FollowUp oldFollowUp = FollowUp.builder()
                .id(oldFollowUpId)
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 8))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        FollowUp savedFollowUp = FollowUp.builder()
                .id(newFollowUpId)
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .memo("새 액션")
                .build();
        AiNextActionRegenerateResponse aiResponse = nextActionAiResponse();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.of(aiInsight));
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of(reason));
        when(followUpRepository.findFirstByCustomerIdOrderByCreatedAtDescIdDesc(customerId))
                .thenReturn(Optional.of(oldFollowUp));
        when(aiConsultationClient.regenerateNextAction(any(AiNextActionRegenerateRequest.class))).thenReturn(aiResponse);
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(oldFollowUp));
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        NextActionRegenerateResponse response = customerService.regenerateNextAction(
                storeId,
                customerId,
                new NextActionRegenerateRequest()
        );

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getOldFollowUpId()).isEqualTo(oldFollowUpId);
        assertThat(response.getOldFollowUpStatus()).isEqualTo(FollowUpStatus.SUPERSEDED);
        assertThat(response.getNewFollowUpId()).isEqualTo(newFollowUpId);
        assertThat(response.getNewFollowUpStatus()).isEqualTo(FollowUpStatus.PENDING);
        assertThat(response.getContactRound()).isEqualTo(2);
        assertThat(response.getRecommendContactDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(response.getPriorityScore()).isEqualTo(82);
        assertThat(oldFollowUp.getStatus()).isEqualTo(FollowUpStatus.SUPERSEDED);
        assertThat(aiInsight.getPriorityScore()).isEqualTo(82);

        verify(aiConsultationClient).regenerateNextAction(argThat(aiRequest ->
                customerId.equals(aiRequest.getCustomer().getCustomerId())
                        && customer.getStatus() == aiRequest.getCustomer().getStatus()
                        && latestConsultation.getId().equals(aiRequest.getLatestConsultation().getConsultationId())
                        && latestConsultation.getSummary().equals(aiRequest.getLatestConsultation().getSummary())
                        && "HOT".equals(aiRequest.getAiAnalysis().getLeadTemperature())
                        && aiRequest.getAiAnalysis().getNonConversionReasons().size() == 1
                        && "SCHEDULE_CONFLICT".equals(aiRequest.getAiAnalysis().getNonConversionReasons().get(0).getReasonType())
        ));
        verify(followUpRepository).save(argThat(followUp ->
                followUp.getCustomer() == customer
                        && followUp.getConsultation() == latestConsultation
                        && followUp.getStatus() == FollowUpStatus.PENDING
                        && followUp.getContactRound() == 2
                        && LocalDate.of(2026, 7, 10).equals(followUp.getRecommendContactDate())
        ));
        verify(followUpAiInsightRepository).save(argThat(insight ->
                insight.getFollowUp() == savedFollowUp
                        && "새 액션".equals(insight.getActionBasis().get("title"))
                        && "새 설명".equals(insight.getActionBasis().get("description"))
        ));
        verify(customerAiInsightRepository).save(aiInsight);
        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getActivityType() == CustomerActivityType.NEXT_ACTION_REGENERATED
                        && timeline.getRelatedType() == ActivityRelatedType.FOLLOW_UP
                        && newFollowUpId.equals(timeline.getRelatedId())
                        && FollowUpStatus.PENDING.equals(timeline.getBeforeValue().get("status"))
                        && FollowUpStatus.PENDING.equals(timeline.getAfterValue().get("status"))
        ));
    }

    @Test
    @DisplayName("다음 최적 액션 재생성은 최근 1차 COMPLETED follow_up 이후 2차 PENDING을 생성한다")
    void regenerateNextActionCreatesSecondRoundAfterFirstCompleted() {
        RegenerateContext context = prepareRegenerateContext(FollowUpStatus.COMPLETED, 1, null, 2);

        NextActionRegenerateResponse response = customerService.regenerateNextAction(
                context.storeId(),
                context.customerId(),
                new NextActionRegenerateRequest()
        );

        assertThat(response.getContactRound()).isEqualTo(2);
        assertThat(response.getNewFollowUpStatus()).isEqualTo(FollowUpStatus.PENDING);
        verify(followUpRepository).save(argThat(followUp ->
                followUp.getStatus() == FollowUpStatus.PENDING
                        && followUp.getContactRound() == 2
        ));
    }

    @Test
    @DisplayName("다음 최적 액션 재생성은 최근 2차 COMPLETED follow_up 이후 3차 PENDING을 생성한다")
    void regenerateNextActionCreatesThirdRoundAfterSecondCompleted() {
        RegenerateContext context = prepareRegenerateContext(FollowUpStatus.COMPLETED, 2, null, 3);

        NextActionRegenerateResponse response = customerService.regenerateNextAction(
                context.storeId(),
                context.customerId(),
                new NextActionRegenerateRequest()
        );

        assertThat(response.getContactRound()).isEqualTo(3);
        assertThat(response.getNewFollowUpStatus()).isEqualTo(FollowUpStatus.PENDING);
        verify(followUpRepository).save(argThat(followUp ->
                followUp.getStatus() == FollowUpStatus.PENDING
                        && followUp.getContactRound() == 3
        ));
    }

    @Test
    @DisplayName("다음 최적 액션 재생성은 최근 3차 COMPLETED follow_up 이후 FOLLOW_UP_ROUND_LIMIT_EXCEEDED 예외가 발생한다")
    void regenerateNextActionRoundLimitExceededAfterThirdCompleted() {
        RegenerateContext context = prepareRegenerateContext(FollowUpStatus.COMPLETED, 3, null, 3);

        assertThatThrownBy(() -> customerService.regenerateNextAction(
                context.storeId(),
                context.customerId(),
                new NextActionRegenerateRequest()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.FOLLOW_UP_ROUND_LIMIT_EXCEEDED);

        verifyNoInteractions(aiConsultationClient);
        verify(followUpRepository, never()).save(any(FollowUp.class));
    }

    @Test
    @DisplayName("고객 상태를 REGISTERED로 변경하면 등록 서비스를 저장하고 기존 PENDING follow_up을 CLOSED 처리한다")
    void updateCustomerStatusRegisteredClosesFollowUp() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID registeredServiceId = UUID.randomUUID();
        Store store = store(storeId);
        Service registeredService = service(registeredServiceId, store, "정규 PT");
        Service consultedService = service(UUID.randomUUID(), store, "체험 PT");
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                consultedService,
                2,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(2)
                .build();
        CustomerStatusUpdateRequest request = customerStatusUpdateRequest(CustomerStatus.REGISTERED, registeredServiceId);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(serviceRepository.findByIdAndStoreIdAndActiveTrue(registeredServiceId, storeId))
                .thenReturn(Optional.of(registeredService));
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));

        CustomerStatusUpdateResponse response = customerService.updateCustomerStatus(storeId, customerId, request);

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getStatus()).isEqualTo(CustomerStatus.REGISTERED);
        assertThat(response.getFollowUpAction()).isEqualTo("CLOSED");
        assertThat(response.isNextActionRegenerationAvailable()).isFalse();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.REGISTERED);
        assertThat(customer.getRegisteredService()).isEqualTo(registeredService);
        assertThat(customer.getRegisteredAt()).isNotNull();
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.CLOSED);
        assertThat(followUp.getContactRound()).isEqualTo(2);
        verify(followUpConversionService).recordConversionIfAbsent(
                customer,
                customer.getRegisteredAt(),
                ConversionSource.UNKNOWN
        );
        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getActivityType() == CustomerActivityType.CUSTOMER_STATUS_CHANGED
                        && timeline.getRelatedType() == ActivityRelatedType.CUSTOMER
                        && customerId.equals(timeline.getRelatedId())
                        && CustomerStatus.PENDING.equals(timeline.getBeforeValue().get("status"))
                        && CustomerStatus.REGISTERED.equals(timeline.getAfterValue().get("status"))
                        && "CLOSED".equals(timeline.getAfterValue().get("followUpAction"))
        ));
        verifyNoInteractions(customerAiInsightRepository, nonConversionReasonRepository, followUpAiInsightRepository);
    }

    @Test
    @DisplayName("고객 상태를 LOST로 변경하면 기존 PENDING follow_up을 CLOSED 처리하고 contactRound를 유지한다")
    void updateCustomerStatusLostClosesFollowUp() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(3)
                .build();
        CustomerStatusUpdateRequest request = customerStatusUpdateRequest(CustomerStatus.LOST, null);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));

        CustomerStatusUpdateResponse response = customerService.updateCustomerStatus(storeId, customerId, request);

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(response.getStatus()).isEqualTo(CustomerStatus.LOST);
        assertThat(response.getFollowUpAction()).isEqualTo("CLOSED");
        assertThat(response.isNextActionRegenerationAvailable()).isFalse();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.LOST);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.CLOSED);
        assertThat(followUp.getContactRound()).isEqualTo(3);
        verify(customerActivityTimelineRepository).save(argThat(timeline ->
                timeline.getActivityType() == CustomerActivityType.CUSTOMER_STATUS_CHANGED
                        && timeline.getRelatedType() == ActivityRelatedType.CUSTOMER
                        && customerId.equals(timeline.getRelatedId())
                        && CustomerStatus.PENDING.equals(timeline.getBeforeValue().get("status"))
                        && CustomerStatus.LOST.equals(timeline.getAfterValue().get("status"))
                        && "CLOSED".equals(timeline.getAfterValue().get("followUpAction"))
        ));
        verifyNoInteractions(serviceRepository, customerAiInsightRepository, nonConversionReasonRepository, followUpAiInsightRepository, followUpConversionService);
    }

    @Test
    @DisplayName("고객 상태를 NO_SHOW로 변경하면 follow_up은 유지하고 재생성 가능 응답을 반환한다")
    void updateCustomerStatusNoShowKeepsFollowUpAndReturnsRegenerationAvailable() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        FollowUp followUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .build();
        CustomerStatusUpdateRequest request = customerStatusUpdateRequest(CustomerStatus.NO_SHOW, null);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.of(followUp));

        CustomerStatusUpdateResponse response = customerService.updateCustomerStatus(storeId, customerId, request);

        assertThat(response.getStatus()).isEqualTo(CustomerStatus.NO_SHOW);
        assertThat(response.getFollowUpAction()).isEqualTo("REGENERATION_AVAILABLE");
        assertThat(response.isNextActionRegenerationAvailable()).isTrue();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.NO_SHOW);
        assertThat(followUp.getStatus()).isEqualTo(FollowUpStatus.PENDING);
        verifyNoInteractions(serviceRepository, customerAiInsightRepository, nonConversionReasonRepository, followUpAiInsightRepository, followUpConversionService);
    }

    private TimelineDetailContext prepareTimelineDetailContext() {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        InflowPathOption inflowPathOption = inflowPathOption(UUID.randomUUID(), store);
        User counselor = user(UUID.randomUUID(), store, "counselor");
        Customer customer = customer(customerId, store, null, inflowPathOption);
        Consultation consultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                service,
                1,
                AiAnalysisStatus.COMPLETED
        );
        return new TimelineDetailContext(store, customer, counselor, consultation);
    }

    private void stubCustomerDetailBase(
            TimelineDetailContext context,
            List<CustomerActivityTimeline> timeline
    ) {
        UUID customerId = context.customer().getId();
        UUID storeId = context.store().getId();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(context.customer()));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(context.consultation()));
        when(consultationSignalService.findCustomerDetailCardSignals(context.consultation().getId()))
                .thenReturn(List.of());
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                .thenReturn(Optional.empty());
        when(customerActivityTimelineRepository.findAllByCustomerIdAndStoreIdOrderByOccurredAtDescCreatedAtDesc(customerId, storeId))
                .thenReturn(timeline);
    }

    private FollowUp followUp(TimelineDetailContext context) {
        return FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(context.customer())
                .consultation(context.consultation())
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .memo("next action")
                .build();
    }

    private record TimelineDetailContext(
            Store store,
            Customer customer,
            User counselor,
            Consultation consultation
    ) {
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

    private MessageTemplateCreateRequest messageTemplateCreateRequest(
            UUID followUpId,
            MessageTonePreset tonePreset,
            MessageVersionType versionType,
            UUID eventId,
            String additionalInstruction
    ) {
        MessageTemplateCreateRequest request = new MessageTemplateCreateRequest();
        ReflectionTestUtils.setField(request, "followUpId", followUpId);
        ReflectionTestUtils.setField(request, "tonePreset", tonePreset);
        ReflectionTestUtils.setField(request, "versionType", versionType);
        ReflectionTestUtils.setField(request, "eventId", eventId);
        ReflectionTestUtils.setField(request, "additionalInstruction", additionalInstruction);
        return request;
    }

    private MessageTemplateMarkSentRequest messageTemplateMarkSentRequest(OffsetDateTime sentAt) {
        MessageTemplateMarkSentRequest request = new MessageTemplateMarkSentRequest();
        ReflectionTestUtils.setField(request, "sentAt", sentAt);
        return request;
    }

    private FollowUpReplyUpdateRequest followUpReplyUpdateRequest(boolean hasReply) {
        FollowUpReplyUpdateRequest request = new FollowUpReplyUpdateRequest();
        ReflectionTestUtils.setField(request, "hasReply", hasReply);
        return request;
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
        return reconsultationCreateRequest(
                serviceId,
                counselorId,
                consultedAt,
                rawText,
                ConsultationRegistrationStatus.PENDING,
                null
        );
    }

    private ReconsultationCreateRequest reconsultationCreateRequest(
            UUID serviceId,
            UUID counselorId,
            OffsetDateTime consultedAt,
            String rawText,
            ConsultationRegistrationStatus registrationStatus,
            UUID registeredServiceId
    ) {
        ReconsultationCreateRequest request = new ReconsultationCreateRequest();
        ReconsultationCreateRequest.ConsultationInfo consultation =
                new ReconsultationCreateRequest.ConsultationInfo();
        ReflectionTestUtils.setField(consultation, "consultedServiceId", serviceId);
        ReflectionTestUtils.setField(consultation, "consultedAt", consultedAt);
        ReflectionTestUtils.setField(consultation, "userId", counselorId);
        ReflectionTestUtils.setField(consultation, "rawText", rawText);
        ReflectionTestUtils.setField(request, "consultation", consultation);
        ReflectionTestUtils.setField(request, "registrationStatus", registrationStatus);
        ReflectionTestUtils.setField(request, "registeredServiceId", registeredServiceId);
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

    private ConsultationSignal consultationSignal(
            Consultation consultation,
            AiCheckSignalKey key,
            String label,
            boolean confirmed,
            String value
    ) {
        return ConsultationSignal.builder()
                .consultation(consultation)
                .signalKey(key)
                .label(label)
                .confirmed(confirmed)
                .value(value)
                .displayOrder(key.getDisplayOrder())
                .build();
    }

    private CustomerAiAnalysisUpdateRequest aiAnalysisUpdateRequest() {
        CustomerAiAnalysisUpdateRequest request = new CustomerAiAnalysisUpdateRequest();
        CustomerAiAnalysisUpdateRequest.NonConversionReasonInfo reason =
                new CustomerAiAnalysisUpdateRequest.NonConversionReasonInfo();
        ReflectionTestUtils.setField(reason, "reasonType", "SCHEDULE_CONFLICT");
        ReflectionTestUtils.setField(reason, "role", "PRIMARY");
        ReflectionTestUtils.setField(reason, "reasonBasis", "일정 조율이 어렵다고 언급했습니다.");
        ReflectionTestUtils.setField(reason, "confidence", "MEDIUM");
        ReflectionTestUtils.setField(request, "summary", "수정된 AI 상담요약");
        ReflectionTestUtils.setField(request, "leadTemperature", "HOT");
        ReflectionTestUtils.setField(request, "temperatureBasis", "수정된 온도 근거");
        ReflectionTestUtils.setField(request, "nonConversionReasons", List.of(reason));
        return request;
    }

    private AiNextActionRegenerateResponse nextActionAiResponse() {
        return AiNextActionRegenerateResponse.builder()
                .priorityScore(82)
                .nextBestAction(AiNextActionRegenerateResponse.NextBestAction.builder()
                        .title("새 액션")
                        .description("새 설명")
                        .build())
                .followUp(AiNextActionRegenerateResponse.FollowUp.builder()
                        .recommendContactDate(LocalDate.of(2026, 7, 10))
                        .memo("새 액션")
                        .build())
                .followUpInsight(AiNextActionRegenerateResponse.FollowUpInsight.builder()
                        .persuasionPoint(Map.of("main", "일정 부담 완화"))
                        .cautionNote("일정 압박은 피합니다.")
                        .actionBasis(Map.of("reason", "일정 조율이 주요 이탈 요인"))
                        .build())
                .build();
    }

    private RegenerateContext prepareRegenerateContext(
            FollowUpStatus latestStatus,
            int latestRound,
            Integer pendingRound,
            int savedRound
    ) {
        UUID storeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID savedFollowUpId = UUID.randomUUID();
        Store store = store(storeId);
        Service service = service(UUID.randomUUID(), store, "PT");
        User counselor = user(UUID.randomUUID(), store, "문형주");
        Customer customer = customer(customerId, store, null, inflowPathOption(UUID.randomUUID(), store));
        Consultation latestConsultation = consultation(
                UUID.randomUUID(),
                customer,
                counselor,
                service,
                2,
                AiAnalysisStatus.COMPLETED
        );
        CustomerAiInsight aiInsight = CustomerAiInsight.builder()
                .customer(customer)
                .leadTemperature("HOT")
                .temperatureBasis("수정된 온도 근거")
                .priorityScore(70)
                .analyzedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .build();
        FollowUp latestFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 8))
                .status(latestStatus)
                .contactRound(latestRound)
                .build();
        FollowUp pendingFollowUp = pendingRound == null
                ? null
                : FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 8))
                .status(FollowUpStatus.PENDING)
                .contactRound(pendingRound)
                .build();
        FollowUp savedFollowUp = FollowUp.builder()
                .id(savedFollowUpId)
                .customer(customer)
                .consultation(latestConsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 10))
                .status(FollowUpStatus.PENDING)
                .contactRound(savedRound)
                .memo("새 액션")
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(consultationRepository.findFirstByCustomerIdOrderBySessionNoDesc(customerId))
                .thenReturn(Optional.of(latestConsultation));
        when(customerAiInsightRepository.findById(customerId)).thenReturn(Optional.of(aiInsight));
        when(nonConversionReasonRepository.findAllByCustomerIdOrderByUpdatedAtDesc(customerId))
                .thenReturn(List.of());
        when(followUpRepository.findFirstByCustomerIdOrderByCreatedAtDescIdDesc(customerId))
                .thenReturn(Optional.of(latestFollowUp));

        boolean roundLimitExceeded = latestStatus == FollowUpStatus.COMPLETED && latestRound >= 3;
        if (!roundLimitExceeded) {
            when(aiConsultationClient.regenerateNextAction(any(AiNextActionRegenerateRequest.class)))
                    .thenReturn(nextActionAiResponse());
            when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(customerId, FollowUpStatus.PENDING))
                    .thenReturn(Optional.ofNullable(pendingFollowUp));
            when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);
        }

        return new RegenerateContext(storeId, customerId);
    }

    private record RegenerateContext(UUID storeId, UUID customerId) {
    }

    private CustomerStatusUpdateRequest customerStatusUpdateRequest(
            CustomerStatus status,
            UUID registeredServiceId
    ) {
        CustomerStatusUpdateRequest request = new CustomerStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "registeredServiceId", registeredServiceId);
        return request;
    }
}
