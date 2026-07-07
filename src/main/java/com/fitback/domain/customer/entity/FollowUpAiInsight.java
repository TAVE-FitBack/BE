package com.fitback.domain.customer.entity;

import com.fitback.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "follow_up_ai_insight")
@Getter
public class FollowUpAiInsight extends BaseTimeEntity {

    @Id
    @Column(name = "follow_up_id", columnDefinition = "uuid")
    private UUID followUpId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_id", nullable = false)
    private FollowUp followUp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "persuasion_point", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> persuasionPoint;

    @Column(name = "caution_note", columnDefinition = "text")
    private String cautionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_basis", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> actionBasis;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;
}
