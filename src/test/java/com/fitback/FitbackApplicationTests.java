package com.fitback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = FitbackApplication.class, properties = {
		"jwt.secret=local-development-secret-key-change-me",
		"ai.fallback-enabled=true",
		"app.auth.email-verification-required=false",
		"spring.mail.username=test@example.com",
		"spring.mail.password=test-password"
})
class FitbackApplicationTests {

	@Test
	void contextLoads() {
	}

}
