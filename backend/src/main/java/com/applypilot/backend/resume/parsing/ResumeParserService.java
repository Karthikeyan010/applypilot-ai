package com.applypilot.backend.resume.parsing;

/** Provider-independent contract. Implementations produce unverified drafts, never persist profiles. */
public interface ResumeParserService {
    ResumeParsingResult parse(String sourceText);
}
