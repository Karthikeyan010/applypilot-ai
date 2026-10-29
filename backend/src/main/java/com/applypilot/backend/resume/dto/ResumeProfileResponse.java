package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeProfileResponse {

    @Size(max = 1000)
    private String fullName;
    @Size(max = 1000)
    @Email
    private String email;
    @Size(max = 1000)
    private String phone;
    @Size(max = 1000)
    private String location;
    @Size(max = 200000)
    private String summary;
    @Size(max = 200000)
    private String originalText;
    @Size(max = 200000)
    private String unmatchedText;

    @Builder.Default
    @Size(max = 500)
    private List<@NotNull @Size(max = 10000) String> skills = new ArrayList<>();

    @Builder.Default
    @Size(max = 100)
    private List<@NotNull @Valid EducationDto> education = new ArrayList<>();
    @Builder.Default
    @Size(max = 100)
    private List<@NotNull @Valid ExperienceDto> experience = new ArrayList<>();
    @Builder.Default
    @Size(max = 100)
    private List<@NotNull @Valid ProjectDto> projects = new ArrayList<>();
}
