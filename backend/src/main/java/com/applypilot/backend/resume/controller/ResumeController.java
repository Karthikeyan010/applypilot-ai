package com.applypilot.backend.resume.controller;

import com.applypilot.backend.resume.dto.ResumeResponse;
import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import com.applypilot.backend.resume.entity.Resume;
import com.applypilot.backend.resume.service.ResumeExtractionService;
import com.applypilot.backend.resume.service.ResumeService;
import com.applypilot.backend.user.entity.User;
import com.applypilot.backend.user.repository.UserRepo;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeService resumeService;
    private final ResumeExtractionService resumeExtractionService;
    private final UserRepo userRepo;

    public ResumeController(ResumeService resumeService, ResumeExtractionService resumeExtractionService, UserRepo userRepo) {
        this.resumeService = resumeService;
        this.resumeExtractionService = resumeExtractionService;
        this.userRepo = userRepo;
    }

    @PostMapping("/upload")
    public ResponseEntity<ResumeResponse> uploadResume(@RequestParam("file")MultipartFile file, Authentication authentication) throws IOException {

        String email = authentication.getName();

        User user = userRepo.findByEmail(email).orElseThrow(()-> new IllegalArgumentException("Authenticated user not found"));

        Resume resume = resumeService.uploadResume(file, user);

        ResumeProfileResponse parsedProfile = null;
        String parsingError = null;
        try {
            parsedProfile = resumeExtractionService.extract(resume.getExtractedText());
            if (parsedProfile == null) {
                throw new IllegalStateException("Parser returned no profile");
            }
        } catch (RuntimeException exception) {
            log.warn("Profile extraction failed for persisted resume {}", resume.getId(), exception);
            parsingError = "Resume saved, but structured profile extraction failed.";
        }

        ResumeResponse resumeResponse = ResumeResponse.builder()
                .id(resume.getId())
                .userId(user.getId())
                .fileName(resume.getFileName())
                .extractedText(resume.getExtractedText())
                .parsedProfile(parsedProfile)
                .parsingError(parsingError)
                .uploadedAt(resume.getUploadedAt())
                .build();

        return ResponseEntity.ok(resumeResponse);
    }
}
