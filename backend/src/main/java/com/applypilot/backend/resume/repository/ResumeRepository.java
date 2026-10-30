package com.applypilot.backend.resume.repository;

import com.applypilot.backend.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Resume r set r.extractionDraft = :draft, r.parsingError = :error where r.id = :id and r.user.id = :userId")
    int updateDraft(@org.springframework.data.repository.query.Param("id") Long id,
                    @org.springframework.data.repository.query.Param("userId") Long userId,
                    @org.springframework.data.repository.query.Param("draft") com.applypilot.backend.resume.parsing.ResumeParsingResult draft,
                    @org.springframework.data.repository.query.Param("error") String error);
    java.util.Optional<Resume> findByIdAndUserId(Long id, Long userId);
    List<Resume> findByUserId(Long userId);
    List<Resume> findByUserIdOrderByUploadedAtDescIdDesc(Long userId);
}
