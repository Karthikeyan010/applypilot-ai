package com.applypilot.backend.resume.extractor;

import com.applypilot.backend.resume.dto.ContactInfoDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContactInfoExtractorTest {

    private final ContactInfoExtractor extractor =
            new ContactInfoExtractor();

    @Test
    void shouldExtractContactInformation() {

        String header = """
                Karthikeyan Karuppaiah
                +44 7901652728 | Newcastle Upon Tyne, United Kingdom | karthikeyan30cse@gmail.com | LinkedIn | GitHub
                """;

        ContactInfoDto result =
                extractor.extract(header);

        assertEquals(
                "Karthikeyan Karuppaiah",
                result.getFullName()
        );

        assertEquals(
                "karthikeyan30cse@gmail.com",
                result.getEmail()
        );

        assertEquals(
                "+44 7901652728",
                result.getPhone()
        );

        assertEquals(
                "Newcastle Upon Tyne, United Kingdom",
                result.getLocation()
        );
    }
}
