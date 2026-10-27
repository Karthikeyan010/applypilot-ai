package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class ResumeProfileResponse {

    private String fullName;
    private String email;
    private String phone;
    private String location;
    private String summary;
    private String originalText;
    private String unmatchedText;

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    private List<EducationDto> education = new ArrayList<>();
    @Builder.Default
    private List<ExperienceDto> experience = new ArrayList<>();
    @Builder.Default
    private List<ProjectDto> projects = new ArrayList<>();
}
