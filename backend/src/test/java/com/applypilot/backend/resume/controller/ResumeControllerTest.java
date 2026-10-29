package com.applypilot.backend.resume.controller;

import com.applypilot.backend.resume.entity.Resume;
import com.applypilot.backend.resume.extractor.ContactInfoExtractor;
import com.applypilot.backend.resume.extractor.SectionExtractor;
import com.applypilot.backend.resume.extractor.SkillsExtractor;
import com.applypilot.backend.resume.repository.ResumeRepository;
import com.applypilot.backend.resume.service.ResumeExtractionService;
import com.applypilot.backend.resume.service.ResumeService;
import com.applypilot.backend.resume.service.RuleBasedResumeExtractionService;
import com.applypilot.backend.user.entity.User;
import com.applypilot.backend.user.repository.UserRepo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeControllerTest {
    private Resume savedResume;
    private ResumeRepository resumes;
    private ResumeExtractionService parser;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        resumes = mock(ResumeRepository.class);
        parser = mock(ResumeExtractionService.class);
        UserRepo users = mock(UserRepo.class);
        User user = User.builder().id(42L).email("jordan@example.com").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(resumes.findByIdAndUserId(7L, 42L)).thenAnswer(invocation -> Optional.of(savedResume));
        when(resumes.save(any(Resume.class))).thenAnswer(invocation -> {
            Resume saved = invocation.getArgument(0);
            saved.setId(7L);
            saved.setUploadedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            savedResume = saved;
            return saved;
        });
        mvc = MockMvcBuilders.standaloneSetup(new ResumeController(new ResumeService(resumes), parser, users)).build();
    }

    @Test
    void shouldReturnSavedSourceAndParsedProfileIncludingBothJobTitleFields() throws Exception {
        var realParser = new RuleBasedResumeExtractionService(new SectionExtractor(),
                new ContactInfoExtractor(), new SkillsExtractor());
        when(parser.extract(anyString())).thenAnswer(invocation -> realParser.extract(invocation.getArgument(0)));

        mvc.perform(multipart("/api/resumes/upload").file(pdf())
                        .principal(new UsernamePasswordAuthenticationToken("jordan@example.com", "unused")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.fileName").value("resume.pdf"))
                .andExpect(jsonPath("$.extractedText").value(source()))
                .andExpect(jsonPath("$.parsedProfile.originalText").value(source()))
                .andExpect(jsonPath("$.parsedProfile.experience[0].jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.parsedProfile.experience[0].jobTittle").value("Engineer"))
                .andExpect(jsonPath("$.parsingError").isEmpty());

        ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
        verify(resumes, times(2)).save(saved.capture());
        assertEquals(source(), saved.getValue().getExtractedText());
        var order = inOrder(resumes, parser);
        order.verify(resumes).save(any(Resume.class));
        order.verify(parser).extract(source());
    }

    @Test
    void shouldReportParsingFailureAsSuccessfulPersistedUpload() throws Exception {
        when(parser.extract(anyString())).thenThrow(new IllegalStateException("internal parser detail"));
        assertSavedUploadWithParsingError();
    }

    @Test
    void shouldReportNullParserResultAsSuccessfulPersistedUpload() throws Exception {
        when(parser.extract(anyString())).thenReturn(null);
        assertSavedUploadWithParsingError();
    }

    private void assertSavedUploadWithParsingError() throws Exception {
        mvc.perform(multipart("/api/resumes/upload").file(pdf())
                        .principal(new UsernamePasswordAuthenticationToken("jordan@example.com", "unused")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.extractedText").value(source()))
                .andExpect(jsonPath("$.parsedProfile").isEmpty())
                .andExpect(jsonPath("$.parsingError").value("Resume saved, but structured profile extraction failed."));
        verify(resumes, times(2)).save(any(Resume.class));
        verify(parser, times(1)).extract(source());
        verify(resumes).findByIdAndUserId(7L, 42L);
        verifyNoMoreInteractions(resumes, parser);
    }

    private String source() {
        return "Jordan Lee" + System.lineSeparator() + "Experience" + System.lineSeparator()
                + "Engineer | Example Inc | 2023 - Present" + System.lineSeparator();
    }

    private MockMultipartFile pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.setLeading(16);
                for (String line : source().lines().toList()) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(bytes);
            return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes.toByteArray());
        }
    }
}
