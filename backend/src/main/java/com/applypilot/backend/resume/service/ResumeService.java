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
