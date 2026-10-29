package com.applypilot.backend.resume.service;

/** Only failures attributable to the submitted upload should become HTTP 400. */
public class InvalidResumeUploadException extends IllegalArgumentException {
    public InvalidResumeUploadException(String message) {
        super(message);
    }
}
