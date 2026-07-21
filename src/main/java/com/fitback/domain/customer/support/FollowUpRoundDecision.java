package com.fitback.domain.customer.support;

public record FollowUpRoundDecision(
        boolean createFollowUp,
        Integer contactRound
) {

    public static FollowUpRoundDecision create(int contactRound) {
        return new FollowUpRoundDecision(true, contactRound);
    }

    public static FollowUpRoundDecision skip() {
        return new FollowUpRoundDecision(false, null);
    }
}
