package com.fitback.domain.customer.entity;

import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "follow_up")
@Getter
public class FollowUp extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @Column(name = "recommend_contact_date", nullable = false)
    private LocalDate recommendContactDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FollowUpStatus status;

    @Builder.Default
    @Column(name = "contact_round", nullable = false)
    private int contactRound = 1;

    @Builder.Default
    @Column(name = "has_reply", nullable = false)
    private boolean hasReply = false;

    @Column(name = "replied_at")
    private OffsetDateTime repliedAt;

    @Column(name = "snoozed_until")
    private LocalDate snoozedUntil;

    @Column(columnDefinition = "text")
    private String memo;

    public void markSuperseded() {
        this.status = FollowUpStatus.SUPERSEDED;
    }

    public void markClosed() {
        this.status = FollowUpStatus.CLOSED;
    }

    public void markCompleted() {
        this.status = FollowUpStatus.COMPLETED;
    }

    public void changeContactRound(int contactRound) {
        validateContactRound(contactRound);
        this.contactRound = contactRound;
    }

    public void updateReply(boolean hasReply, OffsetDateTime repliedAt) {
        this.hasReply = hasReply;
        this.repliedAt = hasReply ? requireRepliedAt(repliedAt) : null;
    }

    public static void validateContactRound(int contactRound) {
        if (contactRound < 1 || contactRound > 3) {
            throw new IllegalArgumentException("contactRound must be between 1 and 3.");
        }
    }

    private OffsetDateTime requireRepliedAt(OffsetDateTime repliedAt) {
        if (repliedAt == null) {
            throw new IllegalArgumentException("repliedAt is required when hasReply is true.");
        }
        return repliedAt;
    }
}
