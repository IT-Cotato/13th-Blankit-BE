package com.cotato.blankit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"jwt.secret=blankit-test-secret-key-for-jwt-must-be-at-least-32-bytes",
		"jwt.access-token-expiration=3600000",
		"jwt.refresh-token-expiration=1209600000"
})
class BlankitApplicationTests {

	@Test
	void contextLoads() {
	}

}
