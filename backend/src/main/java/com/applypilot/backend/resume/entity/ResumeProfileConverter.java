package com.applypilot.backend.resume.entity;
import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Nullable JSON in SQL TEXT supports existing rows and PostgreSQL/H2. */
@Converter
public class ResumeProfileConverter implements AttributeConverter<ResumeProfileResponse, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public String convertToDatabaseColumn(ResumeProfileResponse profile) {
        if (profile == null) return null;
        try { return JSON.writeValueAsString(profile); }
        catch (Exception ex) { throw new IllegalStateException("Unable to store resume profile", ex); }
    }
    public ResumeProfileResponse convertToEntityAttribute(String json) {
        if (json == null) return null;
        try { return JSON.readValue(json, ResumeProfileResponse.class); }
        catch (Exception ex) { throw new IllegalStateException("Unable to read resume profile", ex); }
    }
}
