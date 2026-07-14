package com.fitback.domain.auth.service;

import com.fitback.domain.auth.dto.request.EmailVerificationRequest;
import com.fitback.domain.auth.dto.request.LoginRequest;
import com.fitback.domain.auth.dto.request.SignupRequest;
import com.fitback.domain.auth.dto.request.TokenRefreshRequest;
import com.fitback.domain.auth.dto.response.LoginResponse;
import com.fitback.domain.auth.dto.response.SignupResponse;
import com.fitback.domain.auth.dto.response.TokenRefreshResponse;
import com.fitback.domain.auth.exception.AuthErrorCode;
import com.fitback.domain.user.entity.User;
import com.fitback.domain.user.enums.UserRole;
import com.fitback.domain.user.exception.UserErrorCode;
import com.fitback.domain.user.repository.UserRepository;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "RT:";
    private static final String EMAIL_VERIFY_PREFIX = "EV:";
    private static final String EMAIL_VERIFIED_PREFIX = "EV:verified:";
    private static final String BLACKLIST_PREFIX = "BL:";
    private static final long EMAIL_VERIFY_EXPIRATION = 60 * 60 * 24L; // 24시간 (초)

    /* 이메일 인증 메일 발송 */
    @Transactional
    public void sendVerification(EmailVerificationRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String token = UUID.randomUUID().toString();

        // Redis에 EV:{token} = email 저장 (TTL 24시간)
        redisTemplate.opsForValue().set(
                EMAIL_VERIFY_PREFIX + token,
                request.getEmail(),
                EMAIL_VERIFY_EXPIRATION,
                TimeUnit.SECONDS
        );

        emailService.sendVerification(request.getEmail(), token);
        log.info("인증 메일 발송 — email: {}", request.getEmail());
    }

    /* 이메일 인증 링크 확인 — User 생성 아직 X Redis에만 인증 완료 표시 */
    @Transactional
    public void verifyEmail(String token) {
        String email = redisTemplate.opsForValue().get(EMAIL_VERIFY_PREFIX + token);

        if (email == null) {
            String verifiedEmail = redisTemplate.opsForValue().get(EMAIL_VERIFIED_PREFIX + token);
            if (verifiedEmail != null) {
                return;
            }
            throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        redisTemplate.opsForValue().set(
                EMAIL_VERIFIED_PREFIX + token,
                email,
                EMAIL_VERIFY_EXPIRATION,
                TimeUnit.SECONDS
        );

        // 미인증 토큰 삭제
        redisTemplate.delete(EMAIL_VERIFY_PREFIX + token);

        log.info("이메일 인증 완료 — email: {}", email);
    }

    /* 회원가입 — 인증 완료된 토큰 + 닉네임/비밀번호로 User 생성 */
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        // 인증 완료된 토큰에서 이메일 조회
        String email = redisTemplate.opsForValue()
                .get(EMAIL_VERIFIED_PREFIX + request.getToken());

        if (email == null) {
            throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(AuthErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_NOT_MATCH);
        }

        if (!request.isAgreeTerms()) {
            throw new BusinessException(AuthErrorCode.TERMS_NOT_AGREED);
        }

        User user = User.builder()
                .email(email)
                .nickname(request.getNickname())
                .role(UserRole.OWNER)
                .password(passwordEncoder.encode(request.getPassword()))
                .agreeTerms(request.isAgreeTerms())
                .agreeMarketing(request.isAgreeMarketing())
                .emailVerified(true)  // 이미 인증 완료된 상태로 생성
                .build();
        userRepository.save(user);

        // 인증 완료 토큰 삭제
        redisTemplate.delete(EMAIL_VERIFIED_PREFIX + request.getToken());

        log.info("회원가입 완료 — email: {}, userId: {}", user.getEmail(), user.getId());

        return SignupResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    /* 로그인 */
    @Transactional
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isEmailVerified()) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        // Redis에 Refresh Token 저장 (TTL 자동 만료)
        long expirationSeconds = jwtTokenProvider.getRefreshExpiration() / 1000;
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + user.getEmail(),
                refreshToken,
                expirationSeconds,
                TimeUnit.SECONDS
        );

        log.info("로그인 성공 — email: {}, userId: {}", user.getEmail(), user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .nickname(user.getNickname())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .storeId(user.getStore() != null ? user.getStore().getId() : null)
                        .build())
                .build();
    }

    /* Token 갱신 */
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {

        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String email = jwtTokenProvider.getEmail(request.getRefreshToken());

        String storedToken = redisTemplate.opsForValue()
                .get(REFRESH_TOKEN_PREFIX + email);

        if (storedToken == null) {
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        if (!storedToken.equals(request.getRefreshToken())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String newAccessToken =
                jwtTokenProvider.generateAccessToken(user.getEmail());

        String newRefreshToken =
                jwtTokenProvider.generateRefreshToken(user.getEmail());

        long expirationSeconds = jwtTokenProvider.getRefreshExpiration() / 1000;

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + user.getEmail(),
                newRefreshToken,
                expirationSeconds,
                TimeUnit.SECONDS
        );

        log.info("토큰 갱신 완료 — email: {}", email);

        return new TokenRefreshResponse(
                newAccessToken,
                newRefreshToken
        );
    }

    /* 로그아웃 */
    public void logout(String email, String accessToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + email);

        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            long remainingMs = jwtTokenProvider.getRemainingExpiration(accessToken);
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + accessToken,
                        "logout",
                        remainingMs,
                        TimeUnit.MILLISECONDS
                );
            }
        }

        log.info("로그아웃 완료 — email: {}", email);
    }
}