package com.fitback.domain.auth.dto.response;

import com.fitback.domain.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private UserInfo user;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private UUID id;
        private String nickname;
        private String email;
        private UserRole role;
        private UUID storeId;
    }
}