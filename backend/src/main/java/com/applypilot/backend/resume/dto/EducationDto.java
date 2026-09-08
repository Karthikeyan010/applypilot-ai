package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EducationDto {

    private String institution;
    private String degree;
    private String fieldOfStudy;
    private String startDate;
    private String endDate;
    private String location;
}
