package com.fitback.domain.customer.dto.response;

import com.fitback.domain.customer.enums.Gender;
import com.fitback.domain.inquiry.enums.InquiryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerManagementInquiryListResponse {

    private List<InquiryItem> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;

    @Getter
    @Builder
    public static class InquiryItem {
        private UUID inquiryId;
        private String name;
        private String phoneNum;
        private Gender gender;
        private LocalDate birthDate;
        private UUID serviceId;
        private String serviceName;
        private UUID inflowPathId;
        private String inflowPathName;
        private String memo;
        private InquiryStatus inquiryStatus;
        private String inquiryStatusName;
        private OffsetDateTime inquiredAt;
        private OffsetDateTime visitScheduledAt;
        private UUID counselorId;
        private String counselorName;
        private boolean converted;
        private UUID convertedCustomerId;
        private UUID convertedConsultationId;
    }
}
