package com.fitback.domain.customer.support;

import com.fitback.domain.customer.entity.FollowUp;
import com.fitback.domain.customer.enums.FollowUpNextActionSource;
import com.fitback.domain.customer.enums.FollowUpStatus;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowUpRoundPolicyTest {

    private final FollowUpRoundPolicy policy = new FollowUpRoundPolicy();

    @Test
    @DisplayName("기존 follow_up이 없으면 1차 follow_up 생성을 결정한다")
    void decideCreatesFirstRoundWhenNoActiveFollowUp() {
        FollowUpRoundDecision decision = policy.decide(
                null,
                FollowUpNextActionSource.MANUAL_REGENERATION
        );

        assertThat(decision.createFollowUp()).isTrue();
        assertThat(decision.contactRound()).isEqualTo(1);
    }

    @Test
    @DisplayName("PENDING follow_up은 현재 차수를 유지한다")
    void decideKeepsCurrentRoundForPendingFollowUp() {
        FollowUpRoundDecision decision = policy.decide(
                followUp(FollowUpStatus.PENDING, 2),
                FollowUpNextActionSource.CONSULTATION_AI_ANALYSIS
        );

        assertThat(decision.createFollowUp()).isTrue();
        assertThat(decision.contactRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("1차 또는 2차 SENT follow_up은 다음 차수로 올린다")
    void decideAdvancesRoundForSentFollowUpBeforeLimit() {
        FollowUpRoundDecision secondRound = policy.decide(
                followUp(FollowUpStatus.SENT, 1),
                FollowUpNextActionSource.MANUAL_REGENERATION
        );
        FollowUpRoundDecision thirdRound = policy.decide(
                followUp(FollowUpStatus.SENT, 2),
                FollowUpNextActionSource.CONSULTATION_AI_ANALYSIS
        );

        assertThat(secondRound.createFollowUp()).isTrue();
        assertThat(secondRound.contactRound()).isEqualTo(2);
        assertThat(thirdRound.createFollowUp()).isTrue();
        assertThat(thirdRound.contactRound()).isEqualTo(3);
    }

    @Test
    @DisplayName("3차 PENDING follow_up은 3차를 유지한다")
    void decideKeepsThirdRoundForPendingThirdRound() {
        FollowUpRoundDecision decision = policy.decide(
                followUp(FollowUpStatus.PENDING, 3),
                FollowUpNextActionSource.CONSULTATION_AI_ANALYSIS
        );

        assertThat(decision.createFollowUp()).isTrue();
        assertThat(decision.contactRound()).isEqualTo(3);
    }

    @Test
    @DisplayName("3차 SENT follow_up의 수동 재생성은 차수 초과 예외를 던진다")
    void decideThrowsForManualRegenerationAfterThirdSent() {
        assertThatThrownBy(() -> policy.decide(
                followUp(FollowUpStatus.SENT, 3),
                FollowUpNextActionSource.MANUAL_REGENERATION
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.FOLLOW_UP_ROUND_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("3차 SENT follow_up의 상담 AI 분석은 follow_up 생성을 스킵한다")
    void decideSkipsForConsultationAiAnalysisAfterThirdSent() {
        FollowUpRoundDecision decision = policy.decide(
                followUp(FollowUpStatus.SENT, 3),
                FollowUpNextActionSource.CONSULTATION_AI_ANALYSIS
        );

        assertThat(decision.createFollowUp()).isFalse();
        assertThat(decision.contactRound()).isNull();
    }

    private FollowUp followUp(FollowUpStatus status, int contactRound) {
        return FollowUp.builder()
                .status(status)
                .contactRound(contactRound)
                .build();
    }
}
