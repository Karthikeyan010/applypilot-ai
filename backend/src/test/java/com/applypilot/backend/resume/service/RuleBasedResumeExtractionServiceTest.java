package com.applypilot.backend.resume.service;

import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import com.applypilot.backend.resume.extractor.ContactInfoExtractor;
import com.applypilot.backend.resume.extractor.SectionExtractor;
import com.applypilot.backend.resume.extractor.SkillsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedResumeExtractionServiceTest {

    private RuleBasedResumeExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new RuleBasedResumeExtractionService(
                new SectionExtractor(),
                new ContactInfoExtractor(),
                new SkillsExtractor()
        );
    }

    @Test
    void shouldReturnEmptyProfileForBlankInput() {
        ResumeProfileResponse result = extractionService.extract("  ");

        assertNotNull(result);
        assertEquals("", result.getOriginalText());
        assertNull(result.getFullName());
        assertTrue(result.getSkills().isEmpty());
        assertTrue(result.getExperience().isEmpty());
        assertTrue(result.getProjects().isEmpty());
        assertTrue(result.getEducation().isEmpty());
    }

    @Test
    void shouldParseMultipleExperienceProjectAndEducationEntries() {
        String resumeText = """
                Jordan Lee
                +1 555 010 1234 | Austin, TX | jordan@example.com | LinkedIn

                Professional Summary
                Backend engineer focused on job application workflows.

                Technical Skills
                Languages: Java, SQL, Python
                Frameworks: Spring Boot, JUnit

                Work Experience
                Senior Backend Engineer | Northstar Apps | Austin, TX | Jan 2022 - Present
                - Built resume upload APIs with PDF text extraction.
                - Reduced job matching latency by 35%.
                Backend Developer | Hiring Tools Inc | Remote | Jun 2019 - Dec 2021
                - Maintained applicant tracking integrations.
                - Added audit logs for recruiter actions.

                Projects
                ApplyPilot Matcher | Java, Spring Boot, PostgreSQL
                - Created rule-based matching reports.
                - Preserved unmatched resume text for review.
                Resume Parser Console | Java, JUnit
                - Split multiple projects into stable response entries.
                - Kept each project description with the correct project.

                Education
                University of Texas | MSc in Computer Science | 2017 - 2019 | Austin, TX
                State College | BSc in Software Engineering | 2013 - 2017 | Dallas, TX

                Awards
                Internal engineering excellence award.
                """;

        ResumeProfileResponse result = extractionService.extract(resumeText);

        assertEquals("Jordan Lee", result.getFullName());
        assertEquals("jordan@example.com", result.getEmail());
        assertEquals("+1 555 010 1234", result.getPhone());
        assertEquals("Austin, TX", result.getLocation());
        assertEquals(5, result.getSkills().size());

        assertEquals(2, result.getExperience().size());
        assertEquals("Senior Backend Engineer", result.getExperience().get(0).getJobTitle());
        assertEquals("Northstar Apps", result.getExperience().get(0).getCompany());
        assertEquals("Jan 2022", result.getExperience().get(0).getStartDate());
        assertEquals("Present", result.getExperience().get(0).getEndDate());
        assertTrue(result.getExperience().get(0).getResponsibilities()
                .contains("Built resume upload APIs with PDF text extraction."));
        assertTrue(result.getExperience().get(1).getResponsibilities()
                .contains("Added audit logs for recruiter actions."));
        assertTrue(result.getExperience().get(1).getOriginalText()
                .contains("Backend Developer | Hiring Tools Inc"));

        assertEquals(2, result.getProjects().size());
        assertEquals("ApplyPilot Matcher", result.getProjects().get(0).getName());
        assertTrue(result.getProjects().get(0).getTechnologies().contains("Spring Boot"));
        assertTrue(result.getProjects().get(0).getDescription()
                .contains("Preserved unmatched resume text for review."));
        assertEquals("Resume Parser Console", result.getProjects().get(1).getName());
        assertTrue(result.getProjects().get(1).getDescription()
                .contains("Kept each project description with the correct project."));

        assertEquals(2, result.getEducation().size());
        assertEquals("University of Texas", result.getEducation().get(0).getInstitution());
        assertEquals("MSc in Computer Science", result.getEducation().get(0).getDegree());
        assertEquals("Computer Science", result.getEducation().get(0).getFieldOfStudy());
        assertEquals("State College", result.getEducation().get(1).getInstitution());

        assertTrue(result.getOriginalText().contains("Internal engineering excellence award."));
        assertTrue(result.getUnmatchedText().contains("ACHIEVEMENTS"));
        assertTrue(result.getUnmatchedText().contains("Internal engineering excellence award."));
    }
}
