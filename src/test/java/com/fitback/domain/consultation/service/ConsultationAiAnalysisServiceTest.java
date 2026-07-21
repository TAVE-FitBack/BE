package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiConsultationAnalyzeRequest;
import com.fitback.domain.consultation.dto.request.AiConsultationGraphSyncRequest;
import com.fitback.domain.consultation.dto.response.AiConsultationAnalyzeResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.entity.ConsultationMaterial;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.enums.ConsultationMaterialType;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.domain.consultation.repository.ConsultationMaterialRepository;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.store.entity.InflowPathOption;
import com.fitback.domain.customer.entity.NonConversionReason;
import com.fitback.domain.customer.enums.CustomerActivityType;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.customer.repository.CustomerActivityTimelineRepository;
import com.fitback.domain.customer.repository.CustomerAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpAiInsightRepository;
import com.fitback.domain.customer.repository.FollowUpRepository;
import com.fitback.domain.customer.repository.NonConversionReasonRepository;
import com.fitback.domain.customer.support.FollowUpRoundPolicy;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationAiAnalysisServiceTest {

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private ConsultationMaterialRepository consultationMaterialRepository;

    @Mock
    private AiConsultationClient aiConsultationClient;

    @Mock
    private CustomerAiInsightRepository customerAiInsightRepository;

    @Mock
    private NonConversionReasonRepository nonConversionReasonRepository;

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private FollowUpAiInsightRepository followUpAiInsightRepository;

    @Mock
    private CustomerActivityTimelineRepository customerActivityTimelineRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ConsultationAiAnalysisService consultationAiAnalysisService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        consultationAiAnalysisService = new ConsultationAiAnalysisService(
                consultationRepository,
                consultationMaterialRepository,
                aiConsultationClient,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                customerActivityTimelineRepository,
                transactionManager,
                new FollowUpRoundPolicy()
        );
        lenient().when(consultationMaterialRepository.findAllByConsultationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("AI 분석 성공 결과를 저장한다")
    void analyzeConsultationSavesSuccessResult() {
        UUID consultationId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Customer customer = customer();
        Service service = service();
        Consultation consultation = Consultation.builder()
                .id(consultationId)
                .customer(customer)
                .user(user(customer.getStore()))
                .consultedService(service)
                .consultedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .sessionNo(1)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.INQUIRY)
                .rawText("문의 전환 상담 원문")
                .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                .build();
        AiConsultationAnalyzeResponse aiResponse = aiResponse();
        FollowUp savedFollowUp = FollowUp.builder()
                .id(followUpId)
                .customer(consultation.getCustomer())
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .memo("가격 부담을 낮춘 시작 옵션을 안내")
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse);
        when(customerAiInsightRepository.findById(consultation.getCustomer().getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(consultation.getCustomer().getId()))
                .thenReturn(Optional.empty());
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(consultation.getSummary()).isEqualTo("가격 부담은 있으나 운동 의지가 있는 고객입니다.");
        assertThat(consultation.getAiParsedAt()).isNotNull();

        ArgumentCaptor<AiConsultationAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(AiConsultationAnalyzeRequest.class);
        verify(aiConsultationClient).analyzeConsultation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCustomer().getCustomerId()).isEqualTo(consultation.getCustomer().getId());
        assertThat(requestCaptor.getValue().getService().getServiceName()).isEqualTo("PT");
        assertThat(requestCaptor.getValue()).isInstanceOf(AiConsultationAnalyzeRequest.class);
        assertThat(requestCaptor.getValue().getConsultation().getSourceType())
                .isEqualTo(ConsultationSourceType.INQUIRY);
        assertThat(requestCaptor.getValue().getConsultation().getRawText())
                .isEqualTo("문의 전환 상담 원문");
        assertThat(requestCaptor.getValue().getAttachedMaterials()).isEmpty();

        ArgumentCaptor<CustomerAiInsight> aiInsightCaptor = ArgumentCaptor.forClass(CustomerAiInsight.class);
        verify(customerAiInsightRepository).save(aiInsightCaptor.capture());
        assertThat(aiInsightCaptor.getValue().getLeadTemperature()).isEqualTo("WARM");
        assertThat(aiInsightCaptor.getValue().getPriorityScore()).isEqualTo(78);

        verify(nonConversionReasonRepository).deleteAllByCustomerId(consultation.getCustomer().getId());
        ArgumentCaptor<List<NonConversionReason>> reasonCaptor = ArgumentCaptor.forClass(List.class);
        verify(nonConversionReasonRepository).saveAll(reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).hasSize(1);
        assertThat(reasonCaptor.getValue().get(0).getReasonType()).isEqualTo("PRICE_BURDEN");

        ArgumentCaptor<FollowUp> followUpCaptor = ArgumentCaptor.forClass(FollowUp.class);
        verify(followUpRepository).save(followUpCaptor.capture());
        assertThat(followUpCaptor.getValue().getStatus()).isEqualTo(FollowUpStatus.PENDING);
        assertThat(followUpCaptor.getValue().getRecommendContactDate()).isEqualTo(LocalDate.of(2026, 7, 3));

        ArgumentCaptor<FollowUpAiInsight> followUpAiInsightCaptor = ArgumentCaptor.forClass(FollowUpAiInsight.class);
        verify(followUpAiInsightRepository).save(followUpAiInsightCaptor.capture());
        assertThat(followUpAiInsightCaptor.getValue().getActionBasis())
                .containsEntry("title", "부담 적은 시작 옵션 제안")
                .containsEntry("description", "큰 패키지보다 시작 부담이 낮은 옵션을 안내합니다.")
                .containsEntry("primaryReason", "PRICE_BURDEN");

        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository, org.mockito.Mockito.times(2)).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getAllValues())
                .extracting(CustomerActivityTimeline::getActivityType)
                .containsExactly(CustomerActivityType.AI_ANALYSIS_COMPLETED, CustomerActivityType.NEXT_ACTION_CREATED);

        verify(consultationRepository, org.mockito.Mockito.times(2)).findById(consultationId);
    }

    @Test
    @DisplayName("상담자료가 있으면 AI 본분석 요청에 attachedMaterials로 포함한다")
    void analyzeConsultationIncludesAttachedMaterials() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);
        ConsultationMaterial material = ConsultationMaterial.builder()
                .id(UUID.randomUUID())
                .consultation(consultation)
                .materialType(ConsultationMaterialType.OTHER)
                .title("kakao-chat")
                .content("카카오톡 상담 기록")
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(consultationMaterialRepository.findAllByConsultationIdOrderByCreatedAtAsc(consultationId))
                .thenReturn(List.of(material));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(consultation.getCustomer().getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(consultation.getCustomer().getId()))
                .thenReturn(Optional.empty());
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(consultation.getCustomer())
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build());

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        ArgumentCaptor<AiConsultationAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(AiConsultationAnalyzeRequest.class);
        verify(aiConsultationClient).analyzeConsultation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getAttachedMaterials()).hasSize(1);
        assertThat(requestCaptor.getValue().getAttachedMaterials().get(0).getMaterialType()).isEqualTo("OTHER");
        assertThat(requestCaptor.getValue().getAttachedMaterials().get(0).getTitle()).isEqualTo("kakao-chat");
        assertThat(requestCaptor.getValue().getAttachedMaterials().get(0).getContent()).isEqualTo("카카오톡 상담 기록");
    }

    @Test
    @DisplayName("기존 PENDING follow_up이 있으면 SUPERSEDED 처리 후 새 PENDING follow_up을 생성한다")
    void analyzeConsultationSupersedesExistingPendingFollowUp() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);
        AiConsultationAnalyzeResponse aiResponse = aiResponse();
        FollowUp existingFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(consultation.getCustomer())
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 2))
                .status(FollowUpStatus.PENDING)
                .build();
        FollowUp savedFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(consultation.getCustomer())
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse);
        when(customerAiInsightRepository.findById(consultation.getCustomer().getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(consultation.getCustomer().getId()))
                .thenReturn(Optional.of(existingFollowUp));
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(existingFollowUp.getStatus()).isEqualTo(FollowUpStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("재상담 회차도 기존 AI 분석 저장 로직을 재사용해 FastAPI 요청과 후속관리 교체를 처리한다")
    void analyzeReconsultationReusesAnalysisFlow() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customer();
        Service service = service();
        Consultation reconsultation = Consultation.builder()
                .id(consultationId)
                .customer(customer)
                .user(user(customer.getStore()))
                .consultedService(service)
                .consultedAt(OffsetDateTime.parse("2026-07-08T15:00:00+09:00"))
                .sessionNo(2)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText("재상담 원문")
                .aiAnalysisStatus(AiAnalysisStatus.PROCESSING)
                .build();
        FollowUp existingFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(reconsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 7))
                .status(FollowUpStatus.PENDING)
                .build();
        FollowUp savedFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(reconsultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(reconsultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(customer.getId()))
                .thenReturn(Optional.of(existingFollowUp));
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        ArgumentCaptor<AiConsultationAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(AiConsultationAnalyzeRequest.class);
        verify(aiConsultationClient).analyzeConsultation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getConsultation().getConsultationId()).isEqualTo(consultationId);
        assertThat(requestCaptor.getValue().getConsultation().getSessionNo()).isEqualTo(2);
        assertThat(requestCaptor.getValue().getConsultation().getRawText()).isEqualTo("재상담 원문");
        assertThat(requestCaptor.getValue().getService().getServiceName()).isEqualTo("PT");
        assertThat(reconsultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(existingFollowUp.getStatus()).isEqualTo(FollowUpStatus.SUPERSEDED);
        verify(nonConversionReasonRepository).deleteAllByCustomerId(customer.getId());
        verify(followUpAiInsightRepository).save(any(FollowUpAiInsight.class));
        verify(customerActivityTimelineRepository, org.mockito.Mockito.times(2)).save(any(CustomerActivityTimeline.class));
    }

    @Test
    @DisplayName("AI 분석은 기존 1차 PENDING follow_up을 같은 1차 PENDING으로 교체한다")
    void analyzeConsultationKeepsFirstRoundForPendingFirstRoundFollowUp() {
        assertAiAnalysisCreatesFollowUpWithRound(FollowUpStatus.PENDING, 1, 1);
    }

    @Test
    @DisplayName("AI 분석은 기존 1차 SENT follow_up을 2차 PENDING으로 교체한다")
    void analyzeConsultationCreatesSecondRoundForSentFirstRoundFollowUp() {
        assertAiAnalysisCreatesFollowUpWithRound(FollowUpStatus.SENT, 1, 2);
    }

    @Test
    @DisplayName("AI 분석은 기존 2차 SENT follow_up을 3차 PENDING으로 교체한다")
    void analyzeConsultationCreatesThirdRoundForSentSecondRoundFollowUp() {
        assertAiAnalysisCreatesFollowUpWithRound(FollowUpStatus.SENT, 2, 3);
    }

    @Test
    @DisplayName("AI 분석은 기존 3차 PENDING follow_up을 같은 3차 PENDING으로 교체한다")
    void analyzeConsultationKeepsThirdRoundForPendingThirdRoundFollowUp() {
        assertAiAnalysisCreatesFollowUpWithRound(FollowUpStatus.PENDING, 3, 3);
    }

    @Test
    @DisplayName("AI 분석은 기존 3차 SENT follow_up이 있으면 분석값만 저장하고 새 follow_up을 생성하지 않는다")
    void analyzeConsultationSkipsFollowUpCreationForSentThirdRoundFollowUp() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customer();
        Service service = service();
        Consultation consultation = consultation(consultationId, customer, service, AiAnalysisStatus.PROCESSING);
        FollowUp existingFollowUp = followUp(customer, consultation, FollowUpStatus.SENT, 3);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(customer.getId()))
                .thenReturn(Optional.of(existingFollowUp));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(consultation.getSummary()).isEqualTo("가격 부담은 있으나 운동 의지가 있는 고객입니다.");
        assertThat(existingFollowUp.getStatus()).isEqualTo(FollowUpStatus.SENT);
        verify(customerAiInsightRepository).save(any(CustomerAiInsight.class));
        verify(nonConversionReasonRepository).deleteAllByCustomerId(customer.getId());
        verify(nonConversionReasonRepository).saveAll(any());
        verify(followUpRepository, never()).save(any(FollowUp.class));
        verify(followUpAiInsightRepository, never()).save(any(FollowUpAiInsight.class));

        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_COMPLETED);
        assertThat(timelineCaptor.getValue().getAfterValue()).containsEntry("followUpId", null);

        ArgumentCaptor<AiConsultationGraphSyncRequest> syncCaptor =
                ArgumentCaptor.forClass(AiConsultationGraphSyncRequest.class);
        verify(aiConsultationClient).syncConsultationGraph(syncCaptor.capture());
        assertThat(syncCaptor.getValue().getFollowUp()).isNull();
        assertThat(syncCaptor.getValue().getFollowUpAiInsight()).isNull();
    }

    @Test
    @DisplayName("REGISTERED 고객은 AI 분석 완료 후 새 PENDING follow_up을 생성하지 않는다")
    void analyzeRegisteredCustomerDoesNotCreateFollowUp() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customerWithStatus(CustomerStatus.REGISTERED);
        Consultation consultation = consultation(consultationId, customer, service(), AiAnalysisStatus.PROCESSING);
        AiConsultationAnalyzeResponse aiResponse = aiResponse();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse);
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        verify(followUpRepository, never()).findActiveByCustomerId(any());
        verify(followUpRepository, never()).save(any(FollowUp.class));
        verify(followUpAiInsightRepository, never()).save(any(FollowUpAiInsight.class));

        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_COMPLETED);
        assertThat(timelineCaptor.getValue().getAfterValue()).containsEntry("followUpId", null);
    }

    @Test
    @DisplayName("PENDING 고객은 follow_up과 follow_up_ai_insight를 포함해 graph sync를 호출한다")
    void analyzePendingCustomerSyncsGraphWithFollowUpAndInsight() {
        UUID consultationId = UUID.randomUUID();
        UUID reasonId = UUID.randomUUID();
        Customer customer = customer();
        Service service = serviceForStore(customer.getStore());
        Consultation consultation = consultation(consultationId, customer, service, AiAnalysisStatus.PROCESSING);
        FollowUp savedFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .memo("가격 부담을 낮춘 시작 옵션을 안내")
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());
        when(nonConversionReasonRepository.saveAll(any())).thenAnswer(invocation -> {
            List<NonConversionReason> reasons = invocation.getArgument(0);
            ReflectionTestUtils.setField(reasons.get(0), "id", reasonId);
            return reasons;
        });
        when(followUpRepository.findActiveByCustomerId(customer.getId()))
                .thenReturn(Optional.empty());
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        ArgumentCaptor<AiConsultationGraphSyncRequest> syncCaptor =
                ArgumentCaptor.forClass(AiConsultationGraphSyncRequest.class);
        verify(aiConsultationClient).syncConsultationGraph(syncCaptor.capture());

        AiConsultationGraphSyncRequest request = syncCaptor.getValue();
        assertThat(request.getStore().getStoreId()).isEqualTo(customer.getStore().getId());
        assertThat(request.getService().getStoreId()).isEqualTo(request.getStore().getStoreId());
        assertThat(request.getCustomer().getStoreId()).isEqualTo(request.getStore().getStoreId());
        assertThat(request.getConsultation().getCustomerId()).isEqualTo(request.getCustomer().getCustomerId());
        assertThat(request.getConsultation().getConsultedServiceId()).isEqualTo(request.getService().getServiceId());
        assertThat(request.getCustomerAiInsight().getCustomerId()).isEqualTo(request.getCustomer().getCustomerId());
        assertThat(request.getNonConversionReasons()).hasSize(1);
        assertThat(request.getNonConversionReasons().get(0).getReasonId()).isEqualTo(reasonId);
        assertThat(request.getNonConversionReasons().get(0).getCustomerId()).isEqualTo(request.getCustomer().getCustomerId());
        assertThat(request.getNonConversionReasons().get(0).getConsultationId()).isEqualTo(request.getConsultation().getConsultationId());
        assertThat(request.getFollowUp()).isNotNull();
        assertThat(request.getFollowUp().getCustomerId()).isEqualTo(request.getCustomer().getCustomerId());
        assertThat(request.getFollowUp().getConsultationId()).isEqualTo(request.getConsultation().getConsultationId());
        assertThat(request.getFollowUpAiInsight()).isNotNull();
        assertThat(request.getFollowUpAiInsight().getFollowUpId()).isEqualTo(request.getFollowUp().getFollowUpId());
    }

    @Test
    @DisplayName("REGISTERED 고객은 follow-up 관련 필드를 null로 graph sync 호출한다")
    void analyzeRegisteredCustomerSyncsGraphWithNullFollowUpFields() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customerWithStatus(CustomerStatus.REGISTERED);
        Service service = serviceForStore(customer.getStore());
        Consultation consultation = consultation(consultationId, customer, service, AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        ArgumentCaptor<AiConsultationGraphSyncRequest> syncCaptor =
                ArgumentCaptor.forClass(AiConsultationGraphSyncRequest.class);
        verify(aiConsultationClient).syncConsultationGraph(syncCaptor.capture());

        assertThat(syncCaptor.getValue().getFollowUp()).isNull();
        assertThat(syncCaptor.getValue().getFollowUpAiInsight()).isNull();
    }

    @Test
    @DisplayName("non_conversion_reason이 없으면 빈 배열로 graph sync 호출한다")
    void analyzeConsultationSyncsGraphWithEmptyNonConversionReasons() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customer();
        Service service = serviceForStore(customer.getStore());
        Consultation consultation = consultation(consultationId, customer, service, AiAnalysisStatus.PROCESSING);
        FollowUp savedFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class)))
                .thenReturn(aiResponseWithoutNonConversionReasons());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(customer.getId()))
                .thenReturn(Optional.empty());
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        ArgumentCaptor<AiConsultationGraphSyncRequest> syncCaptor =
                ArgumentCaptor.forClass(AiConsultationGraphSyncRequest.class);
        verify(aiConsultationClient).syncConsultationGraph(syncCaptor.capture());

        assertThat(syncCaptor.getValue().getNonConversionReasons()).isEmpty();
    }

    @Test
    @DisplayName("graph sync 실패는 예외로 전파하지 않고 RDS 저장 결과를 유지한다")
    void analyzeConsultationKeepsSavedResultWhenGraphSyncFails() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customer();
        Service service = serviceForStore(customer.getStore());
        Consultation consultation = consultation(consultationId, customer, service, AiAnalysisStatus.PROCESSING);
        FollowUp savedFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(customer.getId()))
                .thenReturn(Optional.empty());
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);
        doThrow(new RuntimeException("sync failed"))
                .when(aiConsultationClient)
                .syncConsultationGraph(any(AiConsultationGraphSyncRequest.class));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(consultation.getSummary()).isEqualTo("가격 부담은 있으나 운동 의지가 있는 고객입니다.");
        verify(customerAiInsightRepository).save(any(CustomerAiInsight.class));
        verify(nonConversionReasonRepository).deleteAllByCustomerId(customer.getId());
        verify(followUpRepository).save(any(FollowUp.class));
        verify(followUpAiInsightRepository).save(any(FollowUpAiInsight.class));
        verify(aiConsultationClient).syncConsultationGraph(any(AiConsultationGraphSyncRequest.class));
    }

    @Test
    @DisplayName("상담이 없으면 로그만 남기고 상태 변경 없이 중단한다")
    void analyzeConsultationSkipsWhenConsultationNotFound() {
        UUID consultationId = UUID.randomUUID();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        verify(consultationRepository).findById(consultationId);
        verifyNoInteractions(aiConsultationClient, customerActivityTimelineRepository);
    }

    @Test
    @DisplayName("고객 조회가 불가능하면 상담 AI 분석 상태를 FAILED로 변경하고 성공 계열 데이터는 생성하지 않는다")
    void analyzeConsultationMarksFailedWhenCustomerMissing() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, null, service(), AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        verify(consultationRepository).findById(consultationId);
        verifyNoInteractions(aiConsultationClient);
        verifyNoSuccessResultSaved();
    }

    @Test
    @DisplayName("서비스 조회가 불가능하면 상담 AI 분석 상태를 FAILED로 변경한다")
    void analyzeConsultationMarksFailedWhenServiceMissing() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), null, AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        verify(consultationRepository).findById(consultationId);
        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_FAILED);
        assertThat(timelineCaptor.getValue().getAfterValue()).containsEntry("status", "FAILED");
        verifyNoInteractions(aiConsultationClient);
        verifyNoSuccessResultSaved();
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 FAILED 상태와 실패 타임라인만 저장한다")
    void analyzeConsultationMarksFailedWhenAiRequestFails() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class)))
                .thenThrow(new BusinessException(ConsultationErrorCode.AI_ANALYSIS_REQUEST_FAILED));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_FAILED);
        assertThat(timelineCaptor.getValue().getAfterValue())
                .containsEntry("status", "FAILED")
                .containsEntry("errorCode", "AI_ANALYSIS_REQUEST_FAILED");
        verifyNoSuccessResultSaved();
    }

    @Test
    @DisplayName("FastAPI 4xx/5xx 실패 시 FAILED 상태와 실패 타임라인만 저장하고 기존 follow_up은 변경하지 않는다")
    void analyzeConsultationMarksFailedWhenAiServerReturnsError() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);
        FollowUp existingFollowUp = FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(consultation.getCustomer())
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(FollowUpStatus.PENDING)
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class)))
                .thenThrow(new BusinessException(ConsultationErrorCode.AI_ANALYSIS_FAILED));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        assertThat(existingFollowUp.getStatus()).isEqualTo(FollowUpStatus.PENDING);
        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_FAILED);
        assertThat(timelineCaptor.getValue().getAfterValue())
                .containsEntry("status", "FAILED")
                .containsEntry("errorCode", "AI_ANALYSIS_FAILED");
        verifyNoSuccessResultSaved();
    }

    @Test
    @DisplayName("FastAPI 응답 파싱 실패 시 FAILED 상태와 실패 타임라인만 저장하고 기존 AI 분석값은 변경하지 않는다")
    void analyzeConsultationMarksFailedWhenAiResponseInvalid() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);
        CustomerAiInsight existingAiInsight = CustomerAiInsight.builder()
                .customer(consultation.getCustomer())
                .leadTemperature("WARM")
                .temperatureBasis("기존 분석 근거")
                .priorityScore(70)
                .analyzedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .build();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class)))
                .thenThrow(new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        assertThat(existingAiInsight.getLeadTemperature()).isEqualTo("WARM");
        assertThat(existingAiInsight.getPriorityScore()).isEqualTo(70);
        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_FAILED);
        assertThat(timelineCaptor.getValue().getAfterValue())
                .containsEntry("status", "FAILED")
                .containsEntry("errorCode", "AI_ANALYSIS_RESPONSE_INVALID");
        verifyNoSuccessResultSaved();
    }

    @Test
    @DisplayName("AI 결과 저장 실패 시 성공 저장은 롤백되고 FAILED 상태와 실패 타임라인만 남긴다")
    void analyzeConsultationMarksFailedWhenSaveFails() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);
        AiConsultationAnalyzeResponse aiResponse = aiResponse();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse);
        when(customerAiInsightRepository.findById(consultation.getCustomer().getId())).thenReturn(Optional.empty());
        when(customerAiInsightRepository.save(any(CustomerAiInsight.class)))
                .thenThrow(new BusinessException(ConsultationErrorCode.AI_ANALYSIS_SAVE_FAILED));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        ArgumentCaptor<CustomerActivityTimeline> timelineCaptor = ArgumentCaptor.forClass(CustomerActivityTimeline.class);
        verify(customerActivityTimelineRepository).save(timelineCaptor.capture());
        assertThat(timelineCaptor.getValue().getActivityType()).isEqualTo(CustomerActivityType.AI_ANALYSIS_FAILED);
        assertThat(timelineCaptor.getValue().getAfterValue())
                .containsEntry("status", "FAILED")
                .containsEntry("errorCode", "AI_ANALYSIS_SAVE_FAILED");
        verify(nonConversionReasonRepository, never()).saveAll(any());
        verify(followUpRepository, never()).save(any());
        verify(followUpAiInsightRepository, never()).save(any());
    }

    @Test
    @DisplayName("매장 또는 방문경로 조회가 불가능하면 상담 AI 분석 상태를 FAILED로 변경한다")
    void analyzeConsultationMarksFailedWhenContextMissing() {
        UUID consultationId = UUID.randomUUID();
        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .status(CustomerStatus.PENDING)
                .firstConsultAt(LocalDate.of(2026, 7, 1))
                .latestConsultAt(LocalDate.of(2026, 7, 1))
                .build();
        Consultation consultation = consultation(consultationId, customer, service(), AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        verify(consultationRepository).findById(consultationId);
    }

    private Consultation consultation(
            UUID consultationId,
            Customer customer,
            Service service,
            AiAnalysisStatus aiAnalysisStatus
    ) {
        return Consultation.builder()
                .id(consultationId)
                .customer(customer)
                .user(user(customer != null ? customer.getStore() : null))
                .consultedService(service)
                .consultedAt(OffsetDateTime.parse("2026-07-01T13:00:00+09:00"))
                .sessionNo(1)
                .stage(ConsultationStage.CONSULTATION)
                .sourceType(ConsultationSourceType.DIRECT)
                .rawText("상담 원문")
                .aiAnalysisStatus(aiAnalysisStatus)
                .build();
    }

    private Customer customer() {
        return customerWithStatus(CustomerStatus.PENDING);
    }

    private Customer customerWithStatus(CustomerStatus status) {
        Store store = store();
        return Customer.builder()
                .id(UUID.randomUUID())
                .store(store)
                .name("김고객")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1995, 1, 1))
                .phoneNum("010-1234-5678")
                .preferredContactChannel(PreferredContactChannel.KAKAO)
                .inflowPathOption(inflowPathOption(store))
                .status(status)
                .firstConsultAt(LocalDate.of(2026, 7, 1))
                .latestConsultAt(LocalDate.of(2026, 7, 1))
                .build();
    }

    private Service service() {
        return serviceForStore(store());
    }

    private Service serviceForStore(Store store) {
        return Service.builder()
                .id(UUID.randomUUID())
                .store(store)
                .name("PT")
                .active(true)
                .build();
    }

    private User user(Store store) {
        return User.builder()
                .id(UUID.randomUUID())
                .store(store)
                .email(UUID.randomUUID() + "@fitback.test")
                .nickname("김코치")
                .role(UserRole.STAFF)
                .password("password")
                .agreeMarketing(false)
                .agreeTerms(true)
                .emailVerified(true)
                .build();
    }

    private Store store() {
        return Store.builder()
                .id(UUID.randomUUID())
                .name("핏백짐")
                .storeType(StoreType.GYM)
                .build();
    }

    private InflowPathOption inflowPathOption(Store store) {
        return InflowPathOption.builder()
                .id(UUID.randomUUID())
                .store(store)
                .name("네이버 검색")
                .displayOrder(1)
                .active(true)
                .build();
    }

    private void verifyNoSuccessResultSaved() {
        verify(customerAiInsightRepository, never()).save(any());
        verify(nonConversionReasonRepository, never()).deleteAllByCustomerId(any());
        verify(nonConversionReasonRepository, never()).saveAll(any());
        verify(followUpRepository, never()).save(any());
        verify(followUpAiInsightRepository, never()).save(any());
    }

    private void assertAiAnalysisCreatesFollowUpWithRound(
            FollowUpStatus activeStatus,
            int activeRound,
            int expectedSavedRound
    ) {
        UUID consultationId = UUID.randomUUID();
        Customer customer = customer();
        Service service = service();
        Consultation consultation = consultation(consultationId, customer, service, AiAnalysisStatus.PROCESSING);
        FollowUp existingFollowUp = followUp(customer, consultation, activeStatus, activeRound);
        FollowUp savedFollowUp = followUp(customer, consultation, FollowUpStatus.PENDING, expectedSavedRound);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));
        when(aiConsultationClient.analyzeConsultation(any(AiConsultationAnalyzeRequest.class))).thenReturn(aiResponse());
        when(customerAiInsightRepository.findById(customer.getId())).thenReturn(Optional.empty());
        when(followUpRepository.findActiveByCustomerId(customer.getId()))
                .thenReturn(Optional.of(existingFollowUp));
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(existingFollowUp.getStatus()).isEqualTo(FollowUpStatus.SUPERSEDED);
        verify(followUpRepository).save(argThat(followUp ->
                followUp.getStatus() == FollowUpStatus.PENDING
                        && followUp.getContactRound() == expectedSavedRound
                        && followUp.getCustomer() == customer
                        && followUp.getConsultation() == consultation
        ));
        verify(followUpAiInsightRepository).save(argThat(insight -> insight.getFollowUp() == savedFollowUp));
    }

    private FollowUp followUp(
            Customer customer,
            Consultation consultation,
            FollowUpStatus status,
            int contactRound
    ) {
        return FollowUp.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .consultation(consultation)
                .recommendContactDate(LocalDate.of(2026, 7, 3))
                .status(status)
                .contactRound(contactRound)
                .memo("가격 부담을 낮춘 시작 옵션을 안내")
                .build();
    }

    private AiConsultationAnalyzeResponse aiResponse() {
        return AiConsultationAnalyzeResponse.builder()
                .summary("가격 부담은 있으나 운동 의지가 있는 고객입니다.")
                .customerInsight(AiConsultationAnalyzeResponse.CustomerInsight.builder()
                        .leadTemperature("WARM")
                        .temperatureBasis("가격 부담은 있으나 등록 의향이 남아 있습니다.")
                        .priorityScore(78)
                        .build())
                .nonConversionReasons(List.of(AiConsultationAnalyzeResponse.NonConversionReason.builder()
                        .reasonType("PRICE_BURDEN")
                        .role("PRIMARY")
                        .reasonBasis("가격이 부담된다고 언급했습니다.")
                        .confidence("HIGH")
                        .build()))
                .nextBestAction(AiConsultationAnalyzeResponse.NextBestAction.builder()
                        .title("부담 적은 시작 옵션 제안")
                        .description("큰 패키지보다 시작 부담이 낮은 옵션을 안내합니다.")
                        .build())
                .followUp(AiConsultationAnalyzeResponse.FollowUp.builder()
                        .recommendContactDate(LocalDate.of(2026, 7, 3))
                        .memo("가격 부담을 낮춘 시작 옵션을 안내")
                        .build())
                .followUpInsight(AiConsultationAnalyzeResponse.FollowUpInsight.builder()
                        .persuasionPoint(Map.of("main", "초기 비용 부담 완화"))
                        .cautionNote("무리한 할인 압박은 피합니다.")
                        .actionBasis(Map.of(
                                "primaryReason", "PRICE_BURDEN",
                                "basis", "가격 부담이 주요 이탈 요인으로 판단됨"
                        ))
                        .build())
                .build();
    }

    private AiConsultationAnalyzeResponse aiResponseWithoutNonConversionReasons() {
        return AiConsultationAnalyzeResponse.builder()
                .summary("가격 부담은 있으나 운동 의지가 있는 고객입니다.")
                .customerInsight(AiConsultationAnalyzeResponse.CustomerInsight.builder()
                        .leadTemperature("WARM")
                        .temperatureBasis("가격 부담은 있으나 등록 의향이 남아 있습니다.")
                        .priorityScore(78)
                        .build())
                .nonConversionReasons(List.of())
                .nextBestAction(AiConsultationAnalyzeResponse.NextBestAction.builder()
                        .title("부담 적은 시작 옵션 제안")
                        .description("큰 패키지보다 시작 부담이 낮은 옵션을 안내합니다.")
                        .build())
                .followUp(AiConsultationAnalyzeResponse.FollowUp.builder()
                        .recommendContactDate(LocalDate.of(2026, 7, 3))
                        .memo("가격 부담을 낮춘 시작 옵션을 안내")
                        .build())
                .followUpInsight(AiConsultationAnalyzeResponse.FollowUpInsight.builder()
                        .persuasionPoint(Map.of("main", "초기 비용 부담 완화"))
                        .cautionNote("무리한 할인 압박은 피합니다.")
                        .actionBasis(Map.of(
                                "primaryReason", "PRICE_BURDEN",
                                "basis", "가격 부담이 주요 이탈 요인으로 판단됨"
                        ))
                        .build())
                .build();
    }
}
