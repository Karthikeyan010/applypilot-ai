package com.applypilot.backend.resume.extractor;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SectionExtractor {

    public Map<String , String> extractSections(String text){

        Map<String, String> sections = new LinkedHashMap<>();

        if(text == null || text.isBlank()){
            return sections;
        }

        String normalizedText = normalizeText(text);

        String[] lines = normalizedText.split("\n");

        String currentSection = "header";

        StringBuilder currentContent = new StringBuilder();

        for(String line : lines){
            String trimmedLine = line.trim();

            if(trimmedLine.isEmpty()){
                continue;
            }

            String detectedSection = detectSection(trimmedLine);

            if(detectedSection != null){

                saveCurrentSection(
                        sections,
                        currentSection,
                        currentContent
                );

                currentSection = detectedSection;

                currentContent = new StringBuilder();


            }else{
                currentContent.append(trimmedLine).append("\n");
            }
        }

        saveCurrentSection(sections, currentSection,currentContent);

        return sections;


    }

    private void saveCurrentSection(Map<String, String> sections, String sectionName, StringBuilder content) {
        String value = content.toString().trim();

        if(!value.isEmpty()){
            sections.put(sectionName,value);
        }
    }

    private String detectSection(String trimmedLine) {
        String normalized = trimmedLine.trim().toLowerCase();

        return switch (normalized){
            case "professional summary",
                 "summary",
                 "profile",
                 "professional profile" ->
                    "summary";

            case "technical skills",
                 "skills",
                 "core skills",
                 "key skills" ->
                    "skills";

            case "experience",
                 "work experience",
                 "professional experience",
                 "employment history" ->
                    "experience";

            case "projects",
                 "project experience",
                 "personal projects",
                 "academic projects" ->
                    "projects";

            case "education",
                 "academic background",
                 "academic qualifications" ->
                    "education";

            case "achievements",
                 "awards",
                 "awards and achievements" ->
                    "achievements";

            default -> null;
        };
    }

    private String normalizeText(String text) {

        return text.replace("\r\n", "\n").replace("\r", "\n");

    }
}
