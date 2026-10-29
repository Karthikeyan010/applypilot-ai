package com.applypilot.backend.resume.service;

import com.applypilot.backend.user.entity.User;
import com.applypilot.backend.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class ResumeServiceTest {

    private ResumeService resumeService;
    private ResumeRepository resumeRepository;

    @BeforeEach
    void setUp(){
        resumeRepository = mock(ResumeRepository.class);
        resumeService = new ResumeService(resumeRepository);
    }

    @Test
    void shouldRejectEmptyFile(){

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                new byte[0]
        );

        User user = new User();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resumeService.uploadResume(file, user)
        );

        assertEquals(
                "Resume file cannot be empty",
                exception.getMessage()
        );
        verifyNoInteractions(resumeRepository);

    }

    @Test
    void shouldRejectNonPdfFile(){

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "hello".getBytes()
        );

        User user = new User();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resumeService.uploadResume(file, user)
        );

        assertEquals(
                "Only PDF resumes are supported",
                exception.getMessage()
        );
        verifyNoInteractions(resumeRepository);

    }
    @Test
    void shouldRejectNullFileWithoutPersistence() {
        assertThrows(IllegalArgumentException.class, () -> resumeService.uploadResume(null, new User()));
        verifyNoInteractions(resumeRepository);
    }

    @Test
    void shouldReplaceProfileWithoutChangingSource() {
        var resume = com.applypilot.backend.resume.entity.Resume.builder()
                .id(7L).fileName("cv.pdf").extractedText("source").parsingError("old failure").build();
        var profile = com.applypilot.backend.resume.dto.ResumeProfileResponse.builder().summary("Edited").build();
        org.mockito.Mockito.when(resumeRepository.findByIdAndUserId(7L, 42L))
                .thenReturn(java.util.Optional.of(resume));
        org.mockito.Mockito.when(resumeRepository.save(resume)).thenReturn(resume);
        var result = resumeService.saveParsedProfile(7L, 42L, profile, null);
        org.junit.jupiter.api.Assertions.assertSame(profile, result.getParsedProfile());
        org.junit.jupiter.api.Assertions.assertNull(result.getParsingError());
        assertEquals("source", result.getExtractedText());
        assertEquals("cv.pdf", result.getFileName());
        org.mockito.Mockito.verify(resumeRepository).save(resume);
    }

    @Test
    void shouldNotWriteWhenOwnerScopedLookupFails() {
        org.mockito.Mockito.when(resumeRepository.findByIdAndUserId(7L, 99L))
                .thenReturn(java.util.Optional.empty());
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> resumeService.saveParsedProfile(7L, 99L,
                        com.applypilot.backend.resume.dto.ResumeProfileResponse.builder().build(), null));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatusCode());
        org.mockito.Mockito.verify(resumeRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
