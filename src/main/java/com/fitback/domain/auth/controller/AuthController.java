package com.fitback.domain.auth.controller;

import com.fitback.domain.auth.dto.request.LoginRequest;
import com.fitback.domain.auth.dto.request.SignupRequest;
import com.fitback.domain.auth.dto.request.TokenRefreshRequest;
import com.fitback.domain.auth.dto.response.LoginResponse;
import com.fitback.domain.auth.dto.response.SignupResponse;
import com.fitback.domain.auth.dto.response.TokenRefreshResponse;
import com.fitback.domain.auth.service.AuthService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    /* 회원가입 */
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "사용자 계정 생성 후 인증 메일 발송")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess("회원가입이 완료되었습니다. 이메일을 인증해주세요.",
                        authService.signup(request)));
    }

    /* 이메일 인증 */
    @GetMapping("/verify-email")
    @Operation(summary = "이메일 인증", description = "메일 링크 클릭 시 이메일 인증 처리")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.onSuccess("이메일 인증이 완료되었습니다.", null));
    }

    /* 로그인 */
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(authService.login(request)));
    }

    /* Token 갱신 */
    @PostMapping("/refresh")
    @Operation(summary = "Token 갱신", description = "Refresh Token으로 새로운 Access Token 발급")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(authService.refresh(request)));
    }
}