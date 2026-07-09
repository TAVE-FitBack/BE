package com.fitback.domain.auth.controller;

import com.fitback.domain.auth.dto.request.EmailVerificationRequest;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    /* 회원가입 */
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일 인증 완료 후 추가 정보 입력하여 회원가입")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess("회원가입이 완료되었습니다.",
                        authService.signup(request)));
    }

    /* 이메일 인증 메일 발송 */
    @PostMapping("/send-verification")
    @Operation(summary = "이메일 인증 메일 발송", description = "이메일 입력 후 인증 메일 발송")
    public ResponseEntity<ApiResponse<Void>> sendVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        authService.sendVerification(request);
        return ResponseEntity.ok(ApiResponse.onSuccess("인증 메일이 발송되었습니다.", null));
    }

    /* 이메일 인증 완료 */
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

    /* 로그아웃 */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token 삭제 및 Access Token 블랙리스트 등록")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal(expression = "user.email") String email,
            HttpServletRequest request
    ) {
        String accessToken = resolveToken(request);
        authService.logout(email, accessToken);
        return ResponseEntity.ok(ApiResponse.onSuccess("로그아웃이 완료되었습니다.", null));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}