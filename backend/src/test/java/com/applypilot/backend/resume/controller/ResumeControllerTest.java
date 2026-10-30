package com.applypilot.backend.resume.controller;

import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import com.applypilot.backend.resume.entity.Resume;
import com.applypilot.backend.resume.parsing.*;
import com.applypilot.backend.resume.repository.ResumeRepository;
import com.applypilot.backend.resume.service.ResumeService;
import com.applypilot.backend.user.entity.User;
import com.applypilot.backend.user.repository.UserRepo;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeControllerTest {
    ResumeRepository resumes;
    ResumeParserService parser;
    MockMvc mvc;
    Resume saved;
    final UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("jordan@example.com", "unused");
    @BeforeEach void setup() {
        resumes = mock(ResumeRepository.class); parser = mock(ResumeParserService.class);
        var users = mock(UserRepo.class);
        var user = User.builder().id(42L).email("jordan@example.com").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(resumes.save(any(Resume.class))).thenAnswer(call -> {
            saved = call.getArgument(0); saved.setId(7L); saved.setUploadedAt(LocalDateTime.now()); return saved;
        });
        when(resumes.findByIdAndUserId(7L,42L)).thenAnswer(call -> Optional.ofNullable(saved));
        when(resumes.updateDraft(eq(7L),eq(42L),any(),any())).thenAnswer(call -> {
            saved.setExtractionDraft(call.getArgument(2)); saved.setParsingError(call.getArgument(3)); return 1;
        });
        mvc = MockMvcBuilders.standaloneSetup(new ResumeController(new ResumeService(resumes),parser,users)).build();
    }
    @Test void invalidUploadsDoNotCallInference() throws Exception {
        for (var file : new MockMultipartFile[]{new MockMultipartFile("file","empty.pdf","application/pdf",new byte[0]),
                new MockMultipartFile("file","bad.pdf","application/pdf",new byte[]{1,2}),
                new MockMultipartFile("file","cv.txt","text/plain",new byte[]{1})}) {
            mvc.perform(multipart("/api/resumes/upload").file(file).principal(principal))
                .andExpect(status().isBadRequest()).andExpect(content().string(""));
        }
        verifyNoInteractions(resumes, parser);
    }
    @Test void uploadProducesDraftNotVerifiedProfile() throws Exception {
        when(parser.parse(anyString())).thenReturn(new ResumeParsingResult(
                ResumeProfileResponse.builder().fullName("Jordan Lee").originalText(source()).build(),List.of(),"ollama","test"));
        mvc.perform(multipart("/api/resumes/upload").file(pdf()).principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile").isEmpty())
            .andExpect(jsonPath("$.extractionDraft.profile.fullName").value("Jordan Lee"))
            .andExpect(jsonPath("$.extractedText").value(source()));
        var order = inOrder(resumes,parser);order.verify(resumes).save(any(Resume.class));order.verify(parser).parse(source());
        verify(resumes,times(1)).save(any(Resume.class));
    }
    @Test void unavailableProviderRetainsSourceAndOffersRetryWithoutFallback() throws Exception {
        when(parser.parse(anyString())).thenThrow(new ResumeParsingException("Ollama unavailable. Retry or enter manually."));
        mvc.perform(multipart("/api/resumes/upload").file(pdf()).principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.extractionDraft").isEmpty())
            .andExpect(jsonPath("$.parsedProfile").isEmpty()).andExpect(jsonPath("$.extractedText").value(source()))
            .andExpect(jsonPath("$.parsingError").value("Ollama unavailable. Retry or enter manually."));
        verify(parser).parse(source());
    }
    @Test void unexpectedAndNullProviderResponsesAreSafe() throws Exception {
        for (boolean crash : new boolean[]{true,false}) {
            reset(parser);
            if(crash)when(parser.parse(anyString())).thenThrow(new IllegalStateException("secret internal detail"));
            mvc.perform(multipart("/api/resumes/upload").file(pdf()).principal(principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parsingError").value("AI extraction failed. Retry extraction or enter your profile manually. Your saved profile is unchanged."));
        }
    }
    @Test void retryChecksOwnershipBeforeInference() throws Exception {
        mvc.perform(post("/api/resumes/7/extraction").principal(principal)).andExpect(status().isNotFound());
        verifyNoInteractions(parser);
    }
    @Test void manualCreationDoesNotUseInference() throws Exception {
        mvc.perform(post("/api/resumes/manual").principal(principal).contentType("application/json").content("{\"name\":\"My career profile\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("My career profile"))
            .andExpect(jsonPath("$.extractedText").value(""));
        verifyNoInteractions(parser);
    }
    @Test void databaseFailureDoesNotLeakDetailsOrCallInference() throws Exception {
        when(resumes.save(any(Resume.class))).thenThrow(new IllegalStateException("private database path"));
        mvc.perform(multipart("/api/resumes/upload").file(pdf()).principal(principal))
            .andExpect(status().isInternalServerError()).andExpect(content().string(""));
        verifyNoInteractions(parser);
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
