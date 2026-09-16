package com.studymatching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.studymatching.support.PostgresTestContainerConfig;
import org.springframework.context.annotation.Import;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class StudyMatchingApiApplicationTests {

	@Test
	void contextLoads() {
	}
}