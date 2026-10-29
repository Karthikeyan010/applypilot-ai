package com.applypilot.backend.resume;

import com.applypilot.backend.auth.security.JwtService;
import com.applypilot.backend.resume.entity.Resume;
import com.applypilot.backend.resume.repository.ResumeRepository;
import com.applypilot.backend.resume.service.ResumeExtractionService;
import com.applypilot.backend.user.entity.User;
import com.applypilot.backend.user.repository.UserRepo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import org.springframework.mock.web.MockMultipartFile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ResumeProfileIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepo users;
    @Autowired ResumeRepository resumes;
    @Autowired JwtService jwt;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    ResumeExtractionService parser;
    MockMvc mvc;
    String ownerToken, otherToken;
    Long id;
    static final String PROFILE = """
        {"fullName":"Corrected Name","email":"owner@example.com","summary":"Engineer",
         "skills":["Java"],"education":[{"institution":"College","degree":"BSc"}],
         "experience":[{"jobTitle":"Engineer","jobTittle":"Engineer","responsibilities":["Built APIs"]}],
         "projects":[{"name":"App","technologies":["SQL"],"description":["Useful"]}],
         "originalText":"editable source","unmatchedText":"Awards"}
        """;
    @BeforeEach void setup() {
        org.mockito.Mockito.reset(parser);
        resumes.deleteAll(); users.deleteAll();
        User owner = users.save(User.builder().email("owner@example.com").fullName("Owner").passwordHash("unused").build());
        User other = users.save(User.builder().email("other@example.com").fullName("Other").passwordHash("unused").build());
        ownerToken = "Bearer " + jwt.generateToken(owner); otherToken = "Bearer " + jwt.generateToken(other);
        id = resumes.save(Resume.builder().user(owner).fileName("cv.pdf").extractedText("immutable PDF text").build()).getId();
        resumes.save(Resume.builder().user(owner).fileName("second.pdf").extractedText("second").build());
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void replacesAndReloadsNestedProfileWithoutChangingSource() throws Exception {
        mvc.perform(get("/api/resumes/"+id).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile").isEmpty());
        mvc.perform(put("/api/resumes/"+id+"/profile").header("Authorization",ownerToken)
            .contentType("application/json").content(PROFILE)).andExpect(status().isOk());
        mvc.perform(get("/api/resumes/"+id).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile.skills[0]").value("Java"))
            .andExpect(jsonPath("$.parsedProfile.education[0].institution").value("College"))
            .andExpect(jsonPath("$.parsedProfile.experience[0].responsibilities[0]").value("Built APIs"))
            .andExpect(jsonPath("$.parsedProfile.projects[0].technologies[0]").value("SQL"))
            .andExpect(jsonPath("$.extractedText").value("immutable PDF text"));
        new TransactionTemplate(transactions).execute(status -> {
            em.clear();
            Resume saved = resumes.findById(id).orElseThrow();
            assertEquals("Corrected Name", saved.getParsedProfile().getFullName());
            assertEquals(2, resumes.findByUserId(saved.getUser().getId()).size());
            return null;
        });
        mvc.perform(put("/api/resumes/"+id+"/profile").header("Authorization",ownerToken)
            .contentType("application/json").content("{\"skills\":null,\"education\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile.fullName").isEmpty())
            .andExpect(jsonPath("$.parsedProfile.skills").isEmpty());
    }
    @Test void rejectsUnauthorizedAndConcealsOtherUsersResumes() throws Exception {
        for (String token : new String[]{"", "Bearer invalid", otherToken}) {
            int expected = token.equals(otherToken) ? 404 : 401;
            mvc.perform(get("/api/resumes/"+id).header("Authorization",token)).andExpect(status().is(expected));
            mvc.perform(put("/api/resumes/"+id+"/profile").header("Authorization",token)
                .contentType("application/json").content(PROFILE)).andExpect(status().is(expected));
        }
        assertNull(resumes.findById(id).orElseThrow().getParsedProfile());
        mvc.perform(get("/api/resumes/999999").header("Authorization",ownerToken)).andExpect(status().isNotFound());
        mvc.perform(put("/api/resumes/999999/profile").header("Authorization",ownerToken)
            .contentType("application/json").content(PROFILE)).andExpect(status().isNotFound());
    }
    @Test void rejectsMalformedAndInvalidNestedPayloads() throws Exception {
        for (String body : new String[]{"null", "{", "{\"email\":\"invalid\"}",
                "{\"skills\":[null]}", "{\"projects\":[null]}",
                "{\"experience\":[{\"company\":\""+"x".repeat(1001)+"\"}]}",
                "{\"projects\":[{\"technologies\":[null]}]}"}) {
            mvc.perform(put("/api/resumes/"+id+"/profile").header("Authorization",ownerToken)
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/resumes/not-a-number").header("Authorization",ownerToken)).andExpect(status().isBadRequest());
        assertNull(resumes.findById(id).orElseThrow().getParsedProfile());
    }

    @Test void uploadPersistsParsedProfileAcrossRequests() throws Exception {
        String json = mvc.perform(multipart("/api/resumes/upload").file(pdf()).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile.fullName").value("Jordan Lee"))
            .andReturn().getResponse().getContentAsString();
        long uploadedId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("id").asLong();
        mvc.perform(get("/api/resumes/"+uploadedId).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile.fullName").value("Jordan Lee"))
            .andExpect(jsonPath("$.extractedText").value(source()));
    }
    @Test void parserFailureRemainsSavedAndCanBeCorrected() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("private detail"))
            .when(parser).extract(org.mockito.ArgumentMatchers.anyString());
        String json = mvc.perform(multipart("/api/resumes/upload").file(pdf()).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsedProfile").isEmpty())
            .andReturn().getResponse().getContentAsString();
        long uploadedId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("id").asLong();
        mvc.perform(get("/api/resumes/"+uploadedId).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parsingError").value("Resume saved, but structured profile extraction failed."));
        mvc.perform(put("/api/resumes/"+uploadedId+"/profile").header("Authorization",ownerToken)
            .contentType("application/json").content(PROFILE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.parsingError").isEmpty()).andExpect(jsonPath("$.extractedText").value(source()));
    }
    private String source() {
        return "Jordan Lee" + System.lineSeparator() + "Experience" + System.lineSeparator()
                + "Engineer | Example Inc | 2023 - Present" + System.lineSeparator();
    }

    private MockMultipartFile pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.setLeading(16);
                for (String line : source().lines().toList()) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(bytes);
            return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes.toByteArray());
        }
    }
}
