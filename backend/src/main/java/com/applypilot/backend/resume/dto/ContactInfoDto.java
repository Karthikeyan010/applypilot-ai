package com.applypilot.backend.resume.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContactInfoDto {

    private String fullName;
    private String email;
    private String phone;
    private String location;
}
