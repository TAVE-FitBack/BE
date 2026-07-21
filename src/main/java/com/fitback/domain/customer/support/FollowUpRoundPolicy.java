package com.fitback.domain.customer.support;

import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.enums.FollowUpNextActionSource;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.global.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class FollowUpRoundPolicy {

    public FollowUpRoundDecision decide(
            FollowUp activeFollowUp,
            FollowUpNextActionSource source
    ) {
        if (activeFollowUp == null) {
            return FollowUpRoundDecision.create(1);
        }

        int currentRound = activeFollowUp.getContactRound();
        FollowUpStatus status = activeFollowUp.getStatus();

        if (status == FollowUpStatus.PENDING) {
            return FollowUpRoundDecision.create(currentRound);
        }

        if (status == FollowUpStatus.SENT) {
            if (currentRound >= 3) {
                if (source == FollowUpNextActionSource.MANUAL_REGENERATION) {
                    throw new BusinessException(CustomerErrorCode.FOLLOW_UP_ROUND_LIMIT_EXCEEDED);
                }
                return FollowUpRoundDecision.skip();
            }
            return FollowUpRoundDecision.create(currentRound + 1);
        }

        return FollowUpRoundDecision.create(1);
    }
}
