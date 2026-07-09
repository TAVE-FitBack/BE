package com.fitback.domain.inquiry.entity;

import com.fitback.domain.consultation.entity.Consultation;
import com.fitback.domain.customer.entity.Customer;
import com.fitback.domain.customer.entity.InflowPathOption;
import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.customer.enums.PreferredContactChannel;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import com.fitback.domain.service.entity.Service;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inquiry")
@Getter
public class Inquiry extends BaseTimeEntity {

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
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "phone_num", nullable = false, length = 20)
    private String phoneNum;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_channel", nullable = false, length = 30)
    private PreferredContactChannel preferredContactChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inflow_path_id", nullable = false)
    private InflowPathOption inflowPathOption;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_status", nullable = false, length = 30)
    private InquiryStatus inquiryStatus;

    @Column(name = "inquired_at", nullable = false)
    private OffsetDateTime inquiredAt;

    @Column(name = "visit_scheduled_at")
    private OffsetDateTime visitScheduledAt;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_customer_id")
    private Customer convertedCustomer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_consultation_id")
    private Consultation convertedConsultation;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    public void markConverted(
            Customer convertedCustomer,
            Consultation convertedConsultation,
            OffsetDateTime convertedAt
    ) {
        this.inquiryStatus = InquiryStatus.CONVERTED;
        this.convertedCustomer = convertedCustomer;
        this.convertedConsultation = convertedConsultation;
        this.convertedAt = convertedAt;
    }
}
