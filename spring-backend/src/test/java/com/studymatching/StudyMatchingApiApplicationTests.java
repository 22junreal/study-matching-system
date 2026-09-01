package com.studymatching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"jwt.access-token-expiration-seconds=3600"
})
class StudyMatchingApiApplicationTests {

	@Test
	void contextLoads() {
	}
}