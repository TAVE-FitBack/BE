package com.fitback.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/*
<Spring Security 의존성>
- 초기 세팅 시 무조건 로그인창이 뜨거나 모든 API 호출이 401 Unauthorized로 막힘
- 초기 단계에는 기본 API 테스트를 하기 위해 전면 허용(permitAll)
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 회원가입 시 비밀번호 암호화를 위한 BCrypt 인코더 빈 등록
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API 서버이므로 CSRF 보안 기능 해제
                .csrf(AbstractHttpConfigurer::disable)

                // 초기 개발 및 엔드포인트 테스트를 위해 모든 요청 접근 허용
                // (추후 JWT 필터가 구현되면 여기에서 도메인별 인가 설정을 진행)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
