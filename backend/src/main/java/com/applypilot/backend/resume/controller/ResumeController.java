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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

        User user = currentUser(authentication);

        Resume resume = resumeService.uploadResume(file, user);

        ResumeProfileResponse parsedProfile = null;
        String parsingError = null;
        try {
            parsedProfile = resumeExtractionService.extract(resume.getExtractedText());
            if (parsedProfile == null) {
                throw new IllegalStateException("Parser returned no profile");
            }
        } catch (RuntimeException exception) {
            log.warn("Profile extraction failed for persisted resume {}", resume.getId());
            parsingError = "Resume saved, but structured profile extraction failed.";
        }

        resume = resumeService.saveParsedProfile(resume.getId(), user.getId(), parsedProfile, parsingError);
        return ResponseEntity.ok(response(resume, user.getId()));
    }

    @GetMapping("/{id}")
    public ResumeResponse getResume(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        return response(resumeService.getResume(id, user.getId()), user.getId());
    }

    @PutMapping("/{id}/profile")
    public ResumeResponse replaceProfile(@PathVariable Long id,
            @Valid @RequestBody ResumeProfileResponse profile, Authentication authentication) {
        User user = currentUser(authentication);
        return response(resumeService.saveParsedProfile(id, user.getId(), profile, null), user.getId());
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private ResumeResponse response(Resume resume, Long userId) {
        return ResumeResponse.builder()
                .id(resume.getId()).userId(userId).fileName(resume.getFileName())
                .extractedText(resume.getExtractedText()).parsedProfile(resume.getParsedProfile())
                .parsingError(resume.getParsingError()).uploadedAt(resume.getUploadedAt()).build();
    }

    @ExceptionHandler({com.applypilot.backend.resume.service.InvalidResumeUploadException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Void> invalidUpload(Exception exception) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler({RuntimeException.class, IOException.class})
    public ResponseEntity<Void> internalFailure(Exception exception) {
        // Preserve framework 4xx handling without exposing internal failure details.
        if (exception instanceof org.springframework.web.ErrorResponse) {
            throw (RuntimeException) exception;
        }
        log.error("Resume request failed ({})", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().build();
    }
}
