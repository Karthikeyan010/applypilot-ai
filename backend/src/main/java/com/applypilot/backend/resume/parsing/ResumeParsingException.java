package com.applypilot.backend.resume.parsing;

/** Only fixed, user-safe messages cross the API; never include provider responses or source text. */
public class ResumeParsingException extends RuntimeException {
    public ResumeParsingException(String message) { super(message); }
}
