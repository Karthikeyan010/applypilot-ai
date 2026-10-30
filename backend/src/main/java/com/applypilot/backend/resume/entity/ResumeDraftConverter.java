package com.applypilot.backend.resume.entity;

import com.applypilot.backend.resume.parsing.ResumeParsingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ResumeDraftConverter implements AttributeConverter<ResumeParsingResult, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public String convertToDatabaseColumn(ResumeParsingResult value) {
        if (value == null) return null;
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Unable to store extraction draft", e); }
    }
    public ResumeParsingResult convertToEntityAttribute(String value) {
        if (value == null) return null;
        try { return JSON.readValue(value, ResumeParsingResult.class); }
        catch (Exception e) { throw new IllegalStateException("Unable to read extraction draft", e); }
    }
}
