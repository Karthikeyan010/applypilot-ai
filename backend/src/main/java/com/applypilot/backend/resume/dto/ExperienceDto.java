package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperienceDto {

    @Size(max = 1000)
    private String company;
    @Size(max = 1000)
    private String jobTitle;
    @Size(max = 1000)
    private String jobTittle;
    @Size(max = 1000)
    private String startDate;
    @Size(max = 1000)
    private String endDate;
    @Size(max = 1000)
    private String location;
    @Size(max = 500)
    private List<@NotNull @Size(max = 10000) String> responsibilities;
    @Size(max = 200000)
    private String originalText;
}
