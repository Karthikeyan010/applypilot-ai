package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExperienceDto {

    private String company;
    private String jobTittle;
    private String startDate;
    private String endDate;
    private String location;
    private List<String> responsibilities;
}
