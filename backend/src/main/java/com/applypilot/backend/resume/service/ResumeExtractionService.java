package com.applypilot.backend.resume.service;

import com.applypilot.backend.resume.dto.ResumeProfileResponse;

public interface ResumeExtractionService {

    ResumeProfileResponse extract(String extractedText);
}
