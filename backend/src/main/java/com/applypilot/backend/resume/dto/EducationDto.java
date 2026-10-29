package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EducationDto {

    @Size(max = 1000)
    private String institution;
    @Size(max = 1000)
    private String degree;
    @Size(max = 1000)
    private String fieldOfStudy;
    @Size(max = 1000)
    private String startDate;
    @Size(max = 1000)
    private String endDate;
    @Size(max = 1000)
    private String location;
    @Size(max = 200000)
    private String originalText;
}
