package com.applypilot.backend.resume.extractor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectionExtractorTest {

    private final SectionExtractor sectionExtractor =
            new SectionExtractor();

    @Test
    void shouldExtractResumeSections() {

        String text = """
                Karthikeyan Karuppaiah
                karthikeyan@example.com

                Professional Summary
                Software Engineer with backend experience.

                Technical Skills
                Java, Python, Docker

                Experience
                Software Engineer
                Company ABC

                Projects
                ApplyMate
                AI-powered job application platform.

                Education
                Newcastle University
                MSc Advanced Computer Science
                """;

        Map<String, String> sections =
                sectionExtractor.extractSections(text);

        assertTrue(sections.containsKey("header"));
        assertTrue(sections.containsKey("summary"));
        assertTrue(sections.containsKey("skills"));
        assertTrue(sections.containsKey("experience"));
        assertTrue(sections.containsKey("projects"));
        assertTrue(sections.containsKey("education"));

        assertTrue(
                sections.get("skills")
                        .contains("Java")
        );
    }
}
