package com.applypilot.backend.resume.extractor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SkillsExtractor {

    public List<String> extract(String skillsText){

        if(skillsText == null || skillsText.isBlank()){
            return List.of();
        }

        Set<String> skills = new LinkedHashSet<>();

        String[] lines = skillsText.split("\n");

        for(String line : lines){

            String trimmedLine = line.trim();

            if(trimmedLine.isEmpty()){
                continue;
            }

            String skillPart = extractSkillPart(trimmedLine);

            String[] values = skillPart.split(",");

            for(String value : values){

                String skill = cleanSkill(value);

                if(!skill.isEmpty())
                {
                    skills.add(skill);
                }
            }
        }

        return new ArrayList<>(skills);
    }

    private String cleanSkill(String skill) {
        return skill.trim().replaceAll("\\s+", " ");
    }

    private String extractSkillPart(String line) {

        int colonIndex = line.indexOf(":");

        if(colonIndex >= 0){
            return line.substring(colonIndex + 1);
        }
        return line;
    }
}
