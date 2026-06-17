package com.fitback.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class SignupResponse {

    private UUID userId;
    private String email;
    private String nickname;
}