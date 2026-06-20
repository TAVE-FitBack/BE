package com.fitback.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@ConditionalOnProperty(name = "fitback.jpa-auditing.enabled", havingValue = "true", matchIfMissing = true)
@EnableJpaAuditing
public class JpaConfig {

    // TODO : QueryDSL 설정 추가 시 빈 등록
}
