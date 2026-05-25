package com.applypilot.backend.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private String token;
    private Long userId;
    private String fullName;
    private String email;
}