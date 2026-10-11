package com.applypilot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.applypilot.backend.resume.controller.ResumeController;
import com.applypilot.backend.resume.service.ResumeExtractionService;
import com.applypilot.backend.resume.repository.ResumeRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=context-test-secret-at-least-32-characters-long"
})
class BackendApplicationTests {

    @Autowired private ResumeController resumeController;
    @Autowired private ResumeExtractionService extractionService;
    @Autowired private ResumeRepository resumeRepository;

	@Test
	void contextLoads() {
		assertNotNull(resumeController);
        assertNotNull(extractionService.extract(""));
        assertEquals(0, resumeRepository.count());
	}

}
