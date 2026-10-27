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
        assertEquals("  ", result.getOriginalText());
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
        assertEquals("2017", result.getEducation().get(0).getStartDate());
        assertEquals("2019", result.getEducation().get(0).getEndDate());
        assertEquals("Austin, TX", result.getEducation().get(0).getLocation());
        assertEquals("State College", result.getEducation().get(1).getInstitution());
        assertEquals("2013", result.getEducation().get(1).getStartDate());
        assertEquals("2017", result.getEducation().get(1).getEndDate());
        assertEquals("Dallas, TX", result.getEducation().get(1).getLocation());

        assertTrue(result.getOriginalText().contains("Internal engineering excellence award."));
        assertTrue(result.getUnmatchedText().contains("ACHIEVEMENTS"));
        assertTrue(result.getUnmatchedText().contains("Internal engineering excellence award."));
    }
    @Test
    void shouldKeepMultilineJobsTogetherAndSeparateAdjacentHeaders() {
        String first = "Software Engineer\nGoogle\nJan 2023 - Present\nRemote\n- Developed REST APIs";
        String second = "Backend Developer\nExample Inc\nJun 2020 - Dec 2022\n- Maintained integrations";
        var result = extractionService.extract("Experience\n" + first + "\n" + second);
        assertEquals(2, result.getExperience().size());
        var job = result.getExperience().getFirst();
        assertEquals("Software Engineer", job.getJobTitle());
        assertEquals(job.getJobTitle(), job.getJobTittle());
        assertEquals("Google", job.getCompany());
        assertEquals("Jan 2023", job.getStartDate());
        assertEquals("Present", job.getEndDate());
        assertEquals("Remote", job.getLocation());
        assertEquals(java.util.List.of("Developed REST APIs"), job.getResponsibilities());
        assertEquals(first, job.getOriginalText());
        var previous = result.getExperience().get(1);
        assertEquals("Backend Developer", previous.getJobTitle());
        assertEquals("Example Inc", previous.getCompany());
        assertEquals("Jun 2020", previous.getStartDate());
        assertEquals("Dec 2022", previous.getEndDate());
        assertEquals(java.util.List.of("Maintained integrations"), previous.getResponsibilities());
        assertEquals(second, previous.getOriginalText());
    }

    @Test
    void shouldParseReviewReproducerAsOneJob() {
        String entry = "Software Engineer\nGoogle\nJan 2023 - Present\n- Developed REST APIs";
        var jobs = extractionService.extract("Experience\n" + entry).getExperience();
        assertEquals(1, jobs.size());
        assertEquals("Software Engineer", jobs.getFirst().getJobTitle());
        assertEquals("Google", jobs.getFirst().getCompany());
        assertEquals("Jan 2023", jobs.getFirst().getStartDate());
        assertEquals("Present", jobs.getFirst().getEndDate());
        assertEquals(java.util.List.of("Developed REST APIs"), jobs.getFirst().getResponsibilities());
        assertEquals(entry, jobs.getFirst().getOriginalText());
    }

    @Test
    void shouldKeepRepeatedDescriptionTextAfterResponsibilitiesStart() {
        String entry = "Engineer | Example | 2023 - Present\n- Developed APIs\nExample\n- Developed APIs";
        var job = extractionService.extract("Experience\n" + entry).getExperience().getFirst();
        assertEquals(java.util.List.of("Developed APIs", "Example", "Developed APIs"), job.getResponsibilities());
        assertEquals(entry, job.getOriginalText());
    }

    @Test
    void shouldSeparateAdjacentMultilineJobsWithoutBullets() {
        var jobs = extractionService.extract("""
                Experience
                Engineer
                Example Corp
                2023 - Present
                Austin, TX
                Developer
                Other Corp
                2020 - 2022
                """).getExperience();
        assertEquals(2, jobs.size());
        assertEquals("Example Corp", jobs.getFirst().getCompany());
        assertEquals("Austin, TX", jobs.getFirst().getLocation());
        assertEquals("Other Corp", jobs.get(1).getCompany());
        assertEquals("2020", jobs.get(1).getStartDate());
    }

    @Test
    void shouldKeepMultilineEducationDatesAndDetailsWithTheirInstitution() {
        String first = "University of Texas\nMSc in Computer Science\n2017 - 2019\nAustin, TX\n- Thesis on parsers";
        String second = "State College\nBSc in Software Engineering\n2013 - 2017\nDallas, TX";
        var education = extractionService.extract("Education\n" + first + "\n" + second).getEducation();
        assertEquals(2, education.size());
        var degree = education.getFirst();
        assertEquals("University of Texas", degree.getInstitution());
        assertEquals("MSc in Computer Science", degree.getDegree());
        assertEquals("Computer Science", degree.getFieldOfStudy());
        assertEquals("2017", degree.getStartDate());
        assertEquals("2019", degree.getEndDate());
        assertEquals("Austin, TX", degree.getLocation());
        assertEquals(first, degree.getOriginalText());
        assertEquals("State College", education.get(1).getInstitution());
        assertEquals("2013", education.get(1).getStartDate());
        assertEquals("2017", education.get(1).getEndDate());
        assertEquals(second, education.get(1).getOriginalText());
    }

    @Test
    void shouldTreatToolsAsProjectMetadataIncludingAfterDescriptions() {
        String first = "Resume Parser\nTechnologies: Java\nTools: Maven, JUnit\n- Preserved source text\nTools: Git";
        String second = "Job Tracker\nTools: PostgreSQL\n- Tracked applications";
        var projects = extractionService.extract("Projects\n" + first + "\n" + second).getProjects();
        assertEquals(2, projects.size());
        assertEquals("Resume Parser", projects.getFirst().getName());
        assertEquals(java.util.List.of("Java", "Maven", "JUnit", "Git"), projects.getFirst().getTechnologies());
        assertEquals(java.util.List.of("Preserved source text"), projects.getFirst().getDescription());
        assertEquals(first, projects.getFirst().getOriginalText());
        assertEquals("Job Tracker", projects.get(1).getName());
        assertEquals(java.util.List.of("PostgreSQL"), projects.get(1).getTechnologies());
        assertEquals(java.util.List.of("Tracked applications"), projects.get(1).getDescription());
        assertEquals(second, projects.get(1).getOriginalText());
    }

    @Test
    void shouldPreserveUnexpectedContentInKnownSectionsAndRepeatedSections() {
        String source = "  Jordan Lee\r\nSkills\r\nLanguages: Java\r\nNotes: available evenings\r\n"
                + "Experience\r\nEngineer | Example | 2023 - Present | Remote | Custom detail\r\n"
                + "Education\r\nCollege | BSc in Computing | 2017 - 2020 | Somewhere | GPA: 4.0\r\n"
                + "Projects\r\nParser | Java\r\n- Handles text\r\nUnexpected project note\r\n"
                + "Awards\r\nCommunity award\r\nCertifications\r\nAWS certificate\r\n"
                + "Skills\r\nTools: Git\r\n";
        var result = extractionService.extract(source);
        assertEquals(source, result.getOriginalText());
        for (String text : java.util.List.of("Notes: available evenings", "Custom detail", "GPA: 4.0",
                "Unexpected project note", "Community award", "AWS certificate", "Languages: Java", "Tools: Git")) {
            assertTrue(result.getUnmatchedText().contains(text), text);
        }
        assertTrue(result.getSkills().containsAll(java.util.List.of("Java", "Git")));
        assertEquals(1, result.getEducation().size());
        assertEquals("College", result.getEducation().getFirst().getInstitution());
        assertTrue(result.getUnmatchedText().contains("CERTIFICATIONS"));
    }

    @Test
    void shouldHandleNullAndMissingSectionsWithoutInventingFields() {
        assertEquals("", extractionService.extract(null).getOriginalText());
        var result = extractionService.extract("Projects\nResume Parser\n- Preserves text");
        assertTrue(result.getExperience().isEmpty());
        assertTrue(result.getEducation().isEmpty());
        assertTrue(result.getSkills().isEmpty());
        assertEquals(1, result.getProjects().size());
        assertTrue(result.getProjects().getFirst().getTechnologies().isEmpty());
        assertNull(result.getEmail());
    }
}
