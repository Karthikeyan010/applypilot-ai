package com.applypilot.backend.resume.extractor;

import com.applypilot.backend.resume.dto.ContactInfoDto;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContactInfoExtractor {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d[\\d\\s()\\-]{7,}\\d)");
    
    public ContactInfoDto extract(String headerText){

        if(headerText == null || headerText.isBlank()){
            return ContactInfoDto.builder().build();
        }

        String normalizedText = normalizeText(headerText);

        String fullName = extractName(normalizedText);
        String email = extractEmail(normalizedText);
        String phone = extractphone(normalizedText);
        String location = extractLocation(normalizedText, email, phone);

        return ContactInfoDto.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .location(location)
                .build();


    }

    private String extractLocation(String text, String email, String phone) {
        String[] lines = text.split("\n");

        for(String line : lines ){

            if(!line.contains("|")){
                continue;
            }
            String[] parts =line.split("\\|");

            for(String part : parts){

                String value = part.trim();

                if(value.isEmpty()){
                    continue;
                }

                if(email != null && value.contains(email)){
                    continue;
                }

                if(phone != null && value.contains(phone)){
                    continue;
                }

                String lowerCaseValue = value.toLowerCase();

                if(lowerCaseValue.contains("linkedin") || lowerCaseValue.contains("github")){
                    continue;
                }
                return value;
            }
        }
        return null;
    }

    private String extractphone(String text) {

        Matcher matcher = PHONE_PATTERN.matcher(text);

        if(matcher.find()){
            return matcher.group().trim();
        }
        return null;
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);

        if(matcher.find()){
            return matcher.group();
        }
        return null;
    }

    private String extractName(String text) {
        String[] lines = text.split("\n");

        for(String line : lines){
            String trimmedLine = line.trim();

            if(!trimmedLine.isEmpty()){
                return trimmedLine;
            }
        }
        return null;
    }

    private String normalizeText(String text) {

        return text
                .replace("\r\n" , "\n")
                .replace("\r", "\n");
    }
}
