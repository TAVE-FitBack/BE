package com.fitback.domain.customer.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FollowUpReplyUpdateRequest {

    @NotNull(message = "답장 여부는 필수입니다.")
    private Boolean hasReply;
}
