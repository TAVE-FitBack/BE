package com.fitback.domain.customer.enums;

public enum FollowUpStatus {
    /**
     * Message has not been sent yet. Visible on the follow-up board.
     */
    PENDING,

    /**
     * Message has been sent. Visible on the follow-up board until the next action is regenerated.
     */
    SENT,

    /**
     * All follow-up rounds are completed. Visible on the ended follow-up list.
     */
    COMPLETED,

    /**
     * Follow-up is closed because no further contact is needed, such as registration or loss.
     */
    CLOSED,

    /**
     * Follow-up was replaced by regenerated next action.
     */
    SUPERSEDED
}
