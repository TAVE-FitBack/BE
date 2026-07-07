package com.fitback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"jwt.secret=fitback-test-jwt-secret-key-for-context-loads-1234567890"
})
class FitbackApplicationTests {

	@Test
	void contextLoads() {
	}

}
