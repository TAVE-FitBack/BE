package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.client.AiConsultationClient;
import com.fitback.domain.consultation.dto.request.AiConsultationAnalyzeRequest;
import com.fitback.domain.consultation.dto.response.AiConsultationAnalyzeResponse;
import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.CustomerActivityTimeline;
import com.fitback.domain.customer.entity.CustomerAiInsight;
import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.entity.FollowUpAiInsight;
import com.fitback.domain.customer.entity.InflowPathOption;
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
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationAiAnalysisServiceTest {

    @Mock
    private ConsultationRepository consultationRepository;

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

    private ConsultationAiAnalysisService consultationAiAnalysisService;

    @BeforeEach
    void setUp() {
        consultationAiAnalysisService = new ConsultationAiAnalysisService(
                consultationRepository,
                aiConsultationClient,
                customerAiInsightRepository,
                nonConversionReasonRepository,
                followUpRepository,
                followUpAiInsightRepository,
                customerActivityTimelineRepository
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("AI 분석 성공 결과를 저장한다")
    void analyzeConsultationSavesSuccessResult() {
        UUID consultationId = UUID.randomUUID();
        UUID followUpId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);
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
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(
                consultation.getCustomer().getId(),
                FollowUpStatus.PENDING
        )).thenReturn(Optional.empty());
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(consultation.getSummary()).isEqualTo("가격 부담은 있으나 운동 의지가 있는 고객입니다.");
        assertThat(consultation.getAiParsedAt()).isNotNull();

        ArgumentCaptor<AiConsultationAnalyzeRequest> requestCaptor = ArgumentCaptor.forClass(AiConsultationAnalyzeRequest.class);
        verify(aiConsultationClient).analyzeConsultation(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCustomer().getCustomerId()).isEqualTo(consultation.getCustomer().getId());
        assertThat(requestCaptor.getValue().getService().getServiceName()).isEqualTo("PT");

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

        verify(consultationRepository).findById(consultationId);
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
        when(followUpRepository.findFirstByCustomerIdAndStatusOrderByRecommendContactDateAsc(
                consultation.getCustomer().getId(),
                FollowUpStatus.PENDING
        )).thenReturn(Optional.of(existingFollowUp));
        when(followUpRepository.save(any(FollowUp.class))).thenReturn(savedFollowUp);

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(existingFollowUp.getStatus()).isEqualTo(FollowUpStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("상담이 없으면 로그만 남기고 상태 변경 없이 중단한다")
    void analyzeConsultationSkipsWhenConsultationNotFound() {
        UUID consultationId = UUID.randomUUID();

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.empty());

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        verify(consultationRepository).findById(consultationId);
        verifyNoMoreInteractions(consultationRepository);
    }

    @Test
    @DisplayName("고객 조회가 불가능하면 상담 AI 분석 상태를 FAILED로 변경한다")
    void analyzeConsultationMarksFailedWhenCustomerMissing() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, null, service(), AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.FAILED);
        verify(consultationRepository).findById(consultationId);
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
                .status(CustomerStatus.PENDING)
                .firstConsultAt(LocalDate.of(2026, 7, 1))
                .latestConsultAt(LocalDate.of(2026, 7, 1))
                .build();
    }

    private Service service() {
        return Service.builder()
                .id(UUID.randomUUID())
                .store(store())
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
}
