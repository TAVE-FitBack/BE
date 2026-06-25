package com.fitback.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/api/v1/auth/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[FitBack] 이메일 인증");
        message.setText("안녕하세요!!\n\n아래 링크를 클릭하여 이메일 인증을 완료해주세요.\n\n"
                + link + "\n\n24시간 동안 유효합니다.");

        mailSender.send(message);
        log.info("인증 메일 발송 완료: {}", to);
    }
}