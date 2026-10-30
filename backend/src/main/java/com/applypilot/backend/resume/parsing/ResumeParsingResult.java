package com.applypilot.backend.resume.parsing;

import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import java.util.List;

public record ResumeParsingResult(ResumeProfileResponse profile, List<ReviewWarning> warnings,
                                  String provider, String model) {
    public record ReviewWarning(String field, String message) {}
}
