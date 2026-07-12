package com.fitback.domain.schedule.entity;

import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.schedule.enums.ScheduleType;
import com.fitback.domain.store.entity.Store;
import com.fitback.domain.user.entity.User;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "schedule")
@Getter
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id")
    private Consultation consultation;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 30)
    private ScheduleType scheduleType;

    @Column(name = "customer_name", length = 50)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(columnDefinition = "text")
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public void update(
            String title,
            ScheduleType scheduleType,
            String customerName,
            Gender gender,
            String serviceName,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String memo
    ) {
        this.title = title;
        this.scheduleType = scheduleType;
        this.customerName = customerName;
        this.gender = gender;
        this.serviceName = serviceName;
        this.startAt = startAt;
        this.endAt = endAt;
        this.memo = memo;
    }
}
