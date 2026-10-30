package com.applypilot.backend.resume.dto;

import java.time.LocalDateTime;

/** History metadata only: never serialize a user entity or resume contents. */
public record ResumeSummaryResponse(Long id, String fileName, LocalDateTime uploadedAt,
                                    boolean hasProfile, boolean hasDraft, String parsingError) {}
