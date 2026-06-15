package com.fitback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
		"jwt.secret=local-development-secret-key-change-me",
		"ai.fallback-enabled=true"
})
class FitbackApplicationTests {

	@Test
	void contextLoads() {
	}

}
