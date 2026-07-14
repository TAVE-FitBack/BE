package com.fitback.domain.customer.support;

import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse.FollowUpManagementStageResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse.FollowUpManagementStageType;
import com.fitback.domain.customer.repository.CustomerManagementQueryRepository.FollowUpStageRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FollowUpManagementStageAssemblerTest {

    private final FollowUpManagementStageAssembler assembler = new FollowUpManagementStageAssembler();

    @Test
    @DisplayName("active PENDING follow_up의 차수를 ROUND 관리단계로 반환한다")
    void assembleRoundStage() {
        for (int round = 1; round <= 3; round++) {
            FollowUpManagementStageResponse response = assembler.assemble(
                    followUp("PENDING", round),
                    null
            );

            assertThat(response.getType()).isEqualTo(FollowUpManagementStageType.ROUND);
            assertThat(response.getContactRound()).isEqualTo(round);
            assertThat(response.getLabel()).isEqualTo(round + "차 연락 대상");
        }
    }

    @Test
    @DisplayName("active follow_up이 없고 최근 3차 COMPLETED이면 완료로 반환한다")
    void assembleCompletedStage() {
        FollowUpManagementStageResponse response = assembler.assemble(
                null,
                followUp("COMPLETED", 3)
        );

        assertThat(response.getType()).isEqualTo(FollowUpManagementStageType.COMPLETED);
        assertThat(response.getContactRound()).isEqualTo(3);
        assertThat(response.getLabel()).isEqualTo("후속관리 완료");
    }

    @Test
    @DisplayName("active follow_up이 없고 최근 CLOSED이면 종료로 반환한다")
    void assembleClosedStage() {
        FollowUpManagementStageResponse response = assembler.assemble(
                null,
                followUp("CLOSED", 2)
        );

        assertThat(response.getType()).isEqualTo(FollowUpManagementStageType.CLOSED);
        assertThat(response.getContactRound()).isEqualTo(2);
        assertThat(response.getLabel()).isEqualTo("후속관리 종료");
    }

    @Test
    @DisplayName("follow_up이 없거나 완료/종료 조건이 아니면 NONE으로 반환한다")
    void assembleNoneStage() {
        FollowUpManagementStageResponse noFollowUp = assembler.assemble(null, null);
        FollowUpManagementStageResponse completedRoundTwo = assembler.assemble(
                null,
                followUp("COMPLETED", 2)
        );

        assertThat(noFollowUp.getType()).isEqualTo(FollowUpManagementStageType.NONE);
        assertThat(noFollowUp.getContactRound()).isNull();
        assertThat(noFollowUp.getLabel()).isNull();
        assertThat(completedRoundTwo.getType()).isEqualTo(FollowUpManagementStageType.NONE);
    }

    private FollowUpStageRow followUp(String status, int contactRound) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-15T10:00:00+09:00");
        return new FollowUpStageRow(
                UUID.randomUUID(),
                status,
                contactRound,
                now,
                now
        );
    }
}
