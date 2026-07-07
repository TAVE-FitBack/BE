package com.fitback.domain.consultation.service;

import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.consultation.repository.ConsultationRepository;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.enums.CustomerStatus;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.store.enums.StoreType;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationAiAnalysisServiceTest {

    @Mock
    private ConsultationRepository consultationRepository;

    private ConsultationAiAnalysisService consultationAiAnalysisService;

    @BeforeEach
    void setUp() {
        consultationAiAnalysisService = new ConsultationAiAnalysisService(consultationRepository);
    }

    @Test
    @DisplayName("consultationId 기준으로 분석 대상 상담과 연관 데이터를 조회한다")
    void analyzeConsultationLoadsAnalysisTarget() {
        UUID consultationId = UUID.randomUUID();
        Consultation consultation = consultation(consultationId, customer(), service(), AiAnalysisStatus.PROCESSING);

        when(consultationRepository.findById(consultationId)).thenReturn(Optional.of(consultation));

        consultationAiAnalysisService.analyzeConsultation(consultationId);

        assertThat(consultation.getAiAnalysisStatus()).isEqualTo(AiAnalysisStatus.PROCESSING);
        verify(consultationRepository).findById(consultationId);
        verifyNoMoreInteractions(consultationRepository);
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
}
