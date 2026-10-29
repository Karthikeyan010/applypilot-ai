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
public class ProjectDto {

    @Size(max = 1000)
    private String name;
    @Size(max = 500)
    private List<@NotNull @Size(max = 10000) String> technologies;
    @Size(max = 500)
    private List<@NotNull @Size(max = 10000) String> description;
    @Size(max = 200000)
    private String originalText;
}
