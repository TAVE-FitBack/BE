package com.fitback.domain.consultation.entity;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewItemRequest;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.domain.inquiry.entity.InquirySignal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "consultation_signal",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_CONSULTATION_SIGNAL_KEY",
                        columnNames = {"consultation_id", "signal_key"}
                )
        }
)
@Getter
public class ConsultationSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_key", nullable = false, length = 50)
    private AiCheckSignalKey signalKey;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false)
    private Boolean confirmed;

    @Column(name = "signal_value", columnDefinition = "text")
    private String value;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ConsultationSignal from(Consultation consultation, AiCheckPreviewItemRequest item) {
        return ConsultationSignal.builder()
                .consultation(consultation)
                .signalKey(item.getKey())
                .label(item.getLabel())
                .confirmed(item.getConfirmed())
                .value(item.getValue())
                .displayOrder(item.getDisplayOrder())
                .build();
    }

    public static ConsultationSignal copyOf(Consultation consultation, InquirySignal inquirySignal) {
        return ConsultationSignal.builder()
                .consultation(consultation)
                .signalKey(inquirySignal.getSignalKey())
                .label(inquirySignal.getLabel())
                .confirmed(inquirySignal.getConfirmed())
                .value(inquirySignal.getValue())
                .displayOrder(inquirySignal.getDisplayOrder())
                .build();
    }
}
