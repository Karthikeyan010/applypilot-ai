package com.applypilot.backend.resume.repository;

import com.applypilot.backend.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    java.util.Optional<Resume> findByIdAndUserId(Long id, Long userId);
    List<Resume> findByUserId(Long userId);
    List<Resume> findByUserIdOrderByUploadedAtDescIdDesc(Long userId);
}
