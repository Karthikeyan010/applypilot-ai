package com.applypilot.backend.resume.service;

import com.applypilot.backend.resume.entity.Resume;
import com.applypilot.backend.resume.repository.ResumeRepository;
import com.applypilot.backend.user.entity.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ResumeService {
    private final ResumeRepository resumeRepository;

    public ResumeService(ResumeRepository resumeRepository){
        this.resumeRepository = resumeRepository;
    }

    public Resume uploadResume(MultipartFile file, User user) throws IOException {

        validateFile(file);

        String extractedText = extractText(file);

        Resume resume = Resume.builder()
                .user(user)
                .fileName(file.getOriginalFilename())
                .extractedText(extractedText)
                .build();

        return resumeRepository.save(resume);


    }

    @Transactional
    public Resume saveParsedProfile(Long id, Long userId, ResumeProfileResponse profile, String error) {
        Resume resume = getResume(id, userId);
        resume.setParsedProfile(profile);
        resume.setParsingError(error);
        return resumeRepository.save(resume);
    }

    @Transactional(readOnly = true)
    public Resume getResume(Long id, Long userId) {
        return resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    private String extractText(MultipartFile file) throws IOException {

        try(PDDocument document = Loader.loadPDF(file.getBytes())){

            PDFTextStripper textStripper = new PDFTextStripper();

            return textStripper.getText(document);
        }
    }

    private void validateFile(MultipartFile file) {

        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException("Resume file cannot be empty");
        }

        if(!"application/pdf".equalsIgnoreCase(file.getContentType())){
            throw new IllegalArgumentException("Only PDF resumes are supported");
        }
    }
}
