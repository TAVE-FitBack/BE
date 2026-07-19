package com.fitback.domain.customer.support;

import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse.FollowUpManagementStageResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse.FollowUpManagementStageType;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.FollowUpStageRow;
import org.springframework.stereotype.Component;

@Component
public class FollowUpManagementStageAssembler {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CLOSED = "CLOSED";

    public FollowUpManagementStageResponse assemble(
            FollowUpStageRow activeFollowUp,
            FollowUpStageRow latestFollowUp
    ) {
        if (activeFollowUp != null && isActiveStatus(activeFollowUp.status())) {
            return FollowUpManagementStageResponse.builder()
                    .type(FollowUpManagementStageType.ROUND)
                    .contactRound(activeFollowUp.contactRound())
                    .label(activeRoundLabel(activeFollowUp.contactRound()))
                    .build();
        }

        if (latestFollowUp != null
                && latestFollowUp.contactRound() == 3
                && STATUS_COMPLETED.equals(latestFollowUp.status())) {
            return FollowUpManagementStageResponse.builder()
                    .type(FollowUpManagementStageType.COMPLETED)
                    .contactRound(3)
                    .label("후속관리 완료")
                    .build();
        }

        if (latestFollowUp != null && STATUS_CLOSED.equals(latestFollowUp.status())) {
            return FollowUpManagementStageResponse.builder()
                    .type(FollowUpManagementStageType.CLOSED)
                    .contactRound(latestFollowUp.contactRound())
                    .label("후속관리 종료")
                    .build();
        }

        return FollowUpManagementStageResponse.builder()
                .type(FollowUpManagementStageType.NONE)
                .contactRound(null)
                .label(null)
                .build();
    }

    private String activeRoundLabel(int contactRound) {
        return contactRound + "차 연락 대상";
    }
    private boolean isActiveStatus(String status) {
        return STATUS_PENDING.equals(status) || STATUS_SENT.equals(status);
    }
}
