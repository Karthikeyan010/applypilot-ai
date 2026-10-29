package com.applypilot.backend.resume;

import com.applypilot.backend.auth.security.*;
import com.applypilot.backend.user.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {
    @Test void databaseFailureIsNotReportedAsInvalidCredentials() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserRepo users = mock(UserRepo.class);
        when(jwt.extractEmail("valid")).thenReturn("owner@example.com");
        when(users.findByEmail("owner@example.com")).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("private database detail"));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid");
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new JwtAuthenticationFilter(jwt, users, mock(CustomUserDetailsService.class))
                .doFilter(request, response, chain);
        assertEquals(500, response.getStatus());
        assertEquals("", response.getContentAsString());
        verifyNoInteractions(chain);
    }
}
