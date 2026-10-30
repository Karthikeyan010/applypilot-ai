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
        resume.setProfileConfirmedAt(java.time.LocalDateTime.now());
        resume.setExtractionDraft(null);
        resume.setParsingError(error);
        return resumeRepository.save(resume);
    }

    public Resume createManual(String name, User user) {
        return resumeRepository.save(Resume.builder().user(user).fileName(name).extractedText("").build());
    }

    @Transactional
    public Resume saveExtractionDraft(Long id, Long userId,
            com.applypilot.backend.resume.parsing.ResumeParsingResult draft, String error) {
        if (resumeRepository.updateDraft(id, userId, draft, error) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found");
        }
        return getResume(id, userId);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.applypilot.backend.resume.dto.ResumeSummaryResponse> listResumes(Long userId) {
        return resumeRepository.findByUserIdOrderByUploadedAtDescIdDesc(userId).stream()
                .map(resume -> new com.applypilot.backend.resume.dto.ResumeSummaryResponse(
                        resume.getId(), resume.getFileName(), resume.getUploadedAt(),
                        resume.getParsedProfile() != null, resume.getExtractionDraft() != null, resume.getParsingError()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Resume getResume(Long id, Long userId) {
        return resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    private String extractText(MultipartFile file) throws IOException {

        byte[] bytes = file.getBytes();
        PDDocument loaded;
        try {
            loaded = Loader.loadPDF(bytes);
        } catch (IOException invalidPdf) {
            throw new InvalidResumeUploadException("Resume must be a readable, unencrypted PDF");
        }
        try(PDDocument document = loaded){

            PDFTextStripper textStripper = new PDFTextStripper();

            return textStripper.getText(document);
        }
    }

    private void validateFile(MultipartFile file) {

        if(file == null || file.isEmpty()){
            throw new InvalidResumeUploadException("Resume file cannot be empty");
        }

        if(!"application/pdf".equalsIgnoreCase(file.getContentType())){
            throw new InvalidResumeUploadException("Only PDF resumes are supported");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                || file.getOriginalFilename().length() > 255) {
            throw new InvalidResumeUploadException("Invalid resume filename");
        }
    }
}
