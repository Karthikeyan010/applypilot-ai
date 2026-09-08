package com.applypilot.backend.resume.controller;

import com.applypilot.backend.resume.dto.ResumeResponse;
import com.applypilot.backend.resume.entity.Resume;
import com.applypilot.backend.resume.service.ResumeService;
import com.applypilot.backend.user.entity.User;
import com.applypilot.backend.user.repository.UserRepo;
import org.springframework.http.ResponseEntity;
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

    private final ResumeService resumeService;
    private final UserRepo userRepo;

    public ResumeController(ResumeService resumeService, UserRepo userRepo) {
        this.resumeService = resumeService;
        this.userRepo = userRepo;
    }

    @PostMapping("/upload")
    public ResponseEntity<ResumeResponse> uploadResume(@RequestParam("file")MultipartFile file, Authentication authentication) throws IOException {

        String email = authentication.getName();

        User user = userRepo.findByEmail(email).orElseThrow(()-> new IllegalArgumentException("Authenticated user not found"));

        Resume resume = resumeService.uploadResume(file, user);

        ResumeResponse resumeResponse = ResumeResponse.builder()
                .id(resume.getId())
                .userId(user.getId())
                .fileName(resume.getFileName())
                .extractedText(resume.getExtractedText())
                .uploadedAt(resume.getUploadedAt())
                .build();

        return ResponseEntity.ok(resumeResponse);
    }
}
