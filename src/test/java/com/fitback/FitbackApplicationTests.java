package com.fitback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestFitbackApplication.class, properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"fitback.jpa-auditing.enabled=false",
		"jwt.secret=local-development-secret-key-change-me",
		"ai.fallback-enabled=true"
})
class FitbackApplicationTests {

	@Test
	void contextLoads() {
	}

}
