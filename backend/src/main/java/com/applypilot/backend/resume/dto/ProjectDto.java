package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProjectDto {

   private String name;
   private List<String> technologies;
   private List<String> description;
}
