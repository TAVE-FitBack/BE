package com.fitback.domain.consultation.entity;

import com.fitback.domain.consultation.enums.AiAnalysisStatus;
import com.fitback.domain.consultation.enums.ConsultationSourceType;
import com.fitback.domain.consultation.enums.ConsultationStage;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.service.entity.Service;
import com.fitback.domain.user.entity.User;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "consultation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_CONSULTATION_CUSTOMER_SESSION",
                        columnNames = {"customer_id", "session_no"}
                )
        }
)
@Getter
public class Consultation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulted_service_id", nullable = false)
    private Service consultedService;

    @Column(name = "consulted_at", nullable = false)
    private OffsetDateTime consultedAt;

    @Column(name = "session_no", nullable = false)
    private Integer sessionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultationStage stage;

    @Column(columnDefinition = "text")
    private String summary;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_analysis_status", nullable = false, length = 20)
    private AiAnalysisStatus aiAnalysisStatus = AiAnalysisStatus.PROCESSING;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private ConsultationSourceType sourceType;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @Column(name = "visit_purpose", columnDefinition = "text")
    private String visitPurpose;

    @Column(name = "experience_note", columnDefinition = "text")
    private String experienceNote;

    @Column(name = "positive_signal", columnDefinition = "text")
    private String positiveSignal;

    @Column(name = "extra_note", columnDefinition = "text")
    private String extraNote;

    @Column(name = "ai_parsed_at")
    private OffsetDateTime aiParsedAt;

    public void completeAiAnalysis(String summary, OffsetDateTime parsedAt) {
        this.summary = summary;
        this.aiAnalysisStatus = AiAnalysisStatus.COMPLETED;
        this.aiParsedAt = parsedAt;
    }

    public void markAiAnalysisFailed() {
        this.aiAnalysisStatus = AiAnalysisStatus.FAILED;
    }
}
