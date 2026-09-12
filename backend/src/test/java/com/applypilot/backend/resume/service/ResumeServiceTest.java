package com.applypilot.backend.resume.service;

import com.applypilot.backend.resume.repository.ResumeRepository;
import com.applypilot.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class ResumeServiceTest {

    private ResumeRepository resumeRepository;
    private ResumeService resumeService;

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
}
