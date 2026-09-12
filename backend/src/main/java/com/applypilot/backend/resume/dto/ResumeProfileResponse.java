package com.applypilot.backend.resume.dto;

import java.util.List;

public class ResumeProfileResponse {

    private String fullName;
    private String email;
    private String phone;
    private String location;

    private List<String> skills;

    private List<EducationDto> education;
    private List<ExperienceDto> experience;
    private List<ProjectDto> projects;

}
