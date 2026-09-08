package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ResumeResponse {

    private Long id;
    private Long userId;
    private String fileName;
    private String extractedText;
    private LocalDateTime uploadedAt;
}
