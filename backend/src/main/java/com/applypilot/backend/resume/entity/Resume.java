package com.applypilot.backend.resume.entity;

import com.applypilot.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Lob
    @Column(name = "extracted_text", nullable = false)
    private String extractedText;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Convert(converter = ResumeProfileConverter.class)
    @Column(name = "parsed_profile", columnDefinition = "text")
    private com.applypilot.backend.resume.dto.ResumeProfileResponse parsedProfile;

    @Column(name = "parsing_error", length = 200)
    private String parsingError;

    @PrePersist
    protected void onCreate(){
        this.uploadedAt = LocalDateTime.now();

    }
}
