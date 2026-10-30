package com.applypilot.backend.resume.service;

import com.applypilot.backend.resume.dto.ContactInfoDto;
import com.applypilot.backend.resume.dto.EducationDto;
import com.applypilot.backend.resume.dto.ExperienceDto;
import com.applypilot.backend.resume.dto.ProjectDto;
import com.applypilot.backend.resume.dto.ResumeProfileResponse;
import com.applypilot.backend.resume.extractor.ContactInfoExtractor;
import com.applypilot.backend.resume.extractor.SectionExtractor;
import com.applypilot.backend.resume.extractor.SkillsExtractor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Legacy regression reference only. Not a Spring bean and never used as an AI fallback.
public class RuleBasedResumeExtractionService implements ResumeExtractionService {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?i)\\b((?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+\\d{4}|\\d{4})\\s*(?:-|\\u2013|\\u2014|to)\\s*((?:present|current|now)|(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+\\d{4}|\\d{4})\\b"
    );
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(?:19|20)\\d{2}\\b");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[\\-\\u2022*]\\s*");
    private static final Pattern DEGREE_PATTERN = Pattern.compile(
            "(?i)\\b(B\\.?Sc\\.?|M\\.?Sc\\.?|B\\.?A\\.?|M\\.?A\\.?|B\\.?Eng\\.?|M\\.?Eng\\.?|Bachelor|Master|MBA|PhD|Doctorate|Diploma|Certificate)\\b"
    );
    private static final Pattern INSTITUTION_PATTERN = Pattern.compile("(?i)\\b(university|college|school|institute)\\b");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?i)^(remote|hybrid|on[- ]?site)$|^[^|]+,\\s*[^|]+$");

    private final SectionExtractor sectionExtractor;
    private final ContactInfoExtractor contactInfoExtractor;
    private final SkillsExtractor skillsExtractor;

    public RuleBasedResumeExtractionService(
            SectionExtractor sectionExtractor,
            ContactInfoExtractor contactInfoExtractor,
            SkillsExtractor skillsExtractor
    ) {
        this.sectionExtractor = sectionExtractor;
        this.contactInfoExtractor = contactInfoExtractor;
        this.skillsExtractor = skillsExtractor;
    }

    @Override
    public ResumeProfileResponse extract(String extractedText) {
        String normalizedText = normalizeText(extractedText);
        Map<String, String> sections = sectionExtractor.extractSections(normalizedText);
        ContactInfoDto contactInfo = contactInfoExtractor.extract(sections.get("header"));

        return ResumeProfileResponse.builder()
                .fullName(contactInfo.getFullName())
                .email(contactInfo.getEmail())
                .phone(contactInfo.getPhone())
                .location(contactInfo.getLocation())
                .summary(blankToNull(sections.get("summary")))
                .originalText(extractedText == null ? "" : extractedText)
                .unmatchedText(buildUnmatchedText(sections))
                .skills(skillsExtractor.extract(sections.get("skills")))
                .experience(parseExperience(sections.get("experience")))
                .projects(parseProjects(sections.get("projects")))
                .education(parseEducation(sections.get("education")))
                .build();
    }

    private List<ExperienceDto> parseExperience(String experienceText) {
        return splitEntries(experienceText, EntryType.EXPERIENCE).stream()
                .map(this::parseExperienceEntry)
                .toList();
    }

    private ExperienceDto parseExperienceEntry(List<String> lines) {
        String originalText = String.join("\n", lines);
        int headerEnd = 0;
        while (headerEnd < lines.size() && !isBulletLine(lines.get(headerEnd))
                && !isDescriptionContinuation(lines.get(headerEnd))) {
            headerEnd++;
        }
        List<String> headerLines = lines.subList(0, headerEnd);
        List<String> responsibilities = lines.subList(headerEnd, lines.size()).stream()
                .map(this::cleanBullet)
                .filter(line -> !line.isBlank())
                .toList();

        String startDate = null;
        String endDate = null;
        String jobTitle = null;
        String company = null;
        String location = null;

        for (String line : headerLines) {
            DateRange dateRange = extractDateRange(line);
            if (dateRange != null) {
                startDate = firstNonBlank(startDate, dateRange.startDate());
                endDate = firstNonBlank(endDate, dateRange.endDate());
            }
        }

        List<String> metadataParts = splitMetadata(headerLines);
        for (String part : metadataParts) {
            if (containsDate(part)) {
                continue;
            }
            if (jobTitle == null) {
                jobTitle = part;
            } else if (company == null) {
                company = part;
            } else if (location == null) {
                location = part;
            }
        }

        return ExperienceDto.builder()
                .jobTitle(jobTitle)
                .jobTittle(jobTitle)
                .company(company)
                .startDate(startDate)
                .endDate(endDate)
                .location(location)
                .responsibilities(responsibilities)
                .originalText(originalText)
                .build();
    }

    private List<ProjectDto> parseProjects(String projectsText) {
        return splitEntries(projectsText, EntryType.PROJECT).stream()
                .map(this::parseProjectEntry)
                .toList();
    }

    private ProjectDto parseProjectEntry(List<String> lines) {
        String originalText = String.join("\n", lines);
        String name = cleanProjectName(lines.getFirst());
        Set<String> technologies = new LinkedHashSet<>();
        List<String> description = new ArrayList<>();

        addTechnologiesFromHeader(lines.getFirst(), technologies);

        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (isTechnologyLine(line)) {
                addTechnologies(line.substring(line.indexOf(":") + 1), technologies);
            } else {
                String cleaned = cleanBullet(line);
                if (!cleaned.isBlank()) {
                    description.add(cleaned);
                }
            }
        }

        return ProjectDto.builder()
                .name(blankToNull(name))
                .technologies(new ArrayList<>(technologies))
                .description(description)
                .originalText(originalText)
                .build();
    }

    private List<EducationDto> parseEducation(String educationText) {
        return splitEntries(educationText, EntryType.EDUCATION).stream()
                .map(this::parseEducationEntry)
                .toList();
    }

    private EducationDto parseEducationEntry(List<String> lines) {
        String originalText = String.join("\n", lines);
        String institution = null;
        String degree = null;
        String fieldOfStudy = null;
        String startDate = null;
        String endDate = null;
        String location = null;

        for (String line : lines) {
            DateRange dateRange = extractDateRange(line);
            if (dateRange != null) {
                startDate = firstNonBlank(startDate, dateRange.startDate());
                endDate = firstNonBlank(endDate, dateRange.endDate());
            }
        }

        for (String part : splitMetadata(lines.stream().filter(line -> !isBulletLine(line)).toList())) {
            if (degree == null && DEGREE_PATTERN.matcher(part).find()) {
                degree = part;
                fieldOfStudy = extractFieldOfStudy(part);
            } else if (institution == null) {
                institution = part;
            } else if (location == null) {
                location = part;
            }
        }

        return EducationDto.builder()
                .institution(institution)
                .degree(degree)
                .fieldOfStudy(fieldOfStudy)
                .startDate(startDate)
                .endDate(endDate)
                .location(location)
                .originalText(originalText)
                .build();
    }

    private List<List<String>> splitEntries(String sectionText, EntryType type) {
        List<List<String>> entries = new ArrayList<>();
        List<String> current = new ArrayList<>();
        List<String> sectionLines = lines(sectionText);

        for (int index = 0; index < sectionLines.size(); index++) {
            String line = sectionLines.get(index);
            if (!current.isEmpty() && startsEntry(sectionLines, index, current, type)) {
                entries.add(current);
                current = new ArrayList<>();
            }

            current.add(line);
        }

        if (!current.isEmpty()) {
            entries.add(current);
        }

        return entries;
    }

    private boolean startsEntry(List<String> lines, int index, List<String> current, EntryType type) {
        String line = lines.get(index);
        if (isBulletLine(line) || isDescriptionContinuation(line) || isTechnologyLine(line)
                || DATE_RANGE_PATTERN.matcher(line).lookingAt() || YEAR_PATTERN.matcher(line).matches()
                || splitMetadata(List.of(line)).isEmpty()) {
            return false;
        }
        boolean hasDescription = current.stream().anyMatch(value ->
                isBulletLine(value) || isDescriptionContinuation(value));
        boolean hasDate = current.stream().anyMatch(this::containsDate);
        if (type == EntryType.EDUCATION) {
            boolean hasDegree = current.stream().anyMatch(value -> DEGREE_PATTERN.matcher(value).find());
            boolean isDegree = DEGREE_PATTERN.matcher(line).find();
            boolean isInstitution = INSTITUTION_PATTERN.matcher(line).find();
            return hasDegree && (isInstitution || (isDegree && hasDate));
        }
        if (LOCATION_PATTERN.matcher(line).find()) {
            return false;
        }
        if (type == EntryType.PROJECT) {
            boolean hasTechnologies = current.stream().anyMatch(this::isTechnologyLine)
                    || current.getFirst().contains("|");
            boolean nextIsMetadataOrDescription = index + 1 < lines.size()
                    && (isTechnologyLine(lines.get(index + 1)) || isBulletLine(lines.get(index + 1))
                    || isDescriptionContinuation(lines.get(index + 1)));
            return (hasDescription || hasTechnologies) && (line.contains("|") || nextIsMetadataOrDescription);
        }
        // A date on its own belongs to the current header. Look ahead from a new title
        // so adjacent multiline headers work even when the preceding job has no bullets.
        boolean upcomingDate = false;
        for (int next = index; next < Math.min(index + 4, lines.size()); next++) {
            if (isBulletLine(lines.get(next)) || isDescriptionContinuation(lines.get(next))) {
                break;
            }
            upcomingDate |= containsDate(lines.get(next));
        }
        return (hasDate || hasDescription) && upcomingDate;
    }

    private boolean isTechnologyLine(String line) {
        return line.toLowerCase(java.util.Locale.ROOT).matches("^(technologies|tech stack|tools):.*");
    }

    private boolean isDescriptionContinuation(String line) {
        String lowerCaseLine = line.toLowerCase(java.util.Locale.ROOT);
        return lowerCaseLine.startsWith("responsibilities:")
                || lowerCaseLine.startsWith("achievements:")
                || lowerCaseLine.startsWith("description:")
                || lowerCaseLine.matches("^(developed|built|maintained|created|implemented|led|reduced|added)\\b.*");
    }

    private List<String> splitMetadata(List<String> lines) {
        List<String> parts = new ArrayList<>();
        for (String line : lines) {
            String withoutDates = DATE_RANGE_PATTERN.matcher(line).replaceAll("").trim();
            for (String part : withoutDates.split("\\|")) {
                String cleaned = part.replaceAll("\\s{2,}", " ").trim();
                cleaned = cleaned.replaceAll("^[,\\-\\u2013\\u2014]+|[,\\-\\u2013\\u2014]+$", "").trim();
                if (!cleaned.isBlank()) {
                    parts.add(cleaned);
                }
            }
        }
        return parts;
    }

    private void addTechnologiesFromHeader(String header, Set<String> technologies) {
        String[] parts = header.split("\\|");
        if (parts.length > 1) {
            for (int index = 1; index < parts.length; index++) {
                addTechnologies(parts[index], technologies);
            }
        }
    }

    private void addTechnologies(String text, Set<String> technologies) {
        for (String value : text.split(",")) {
            String cleaned = value.trim();
            if (!cleaned.isBlank() && !containsDate(cleaned)) {
                technologies.add(cleaned);
            }
        }
    }

    private String cleanProjectName(String line) {
        return line.split("\\|")[0].trim();
    }

    private String extractFieldOfStudy(String degree) {
        Matcher matcher = Pattern.compile("(?i)\\bin\\s+(.+)$").matcher(degree);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private DateRange extractDateRange(String line) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line);
        if (matcher.find()) {
            return new DateRange(matcher.group(1).trim(), matcher.group(2).trim());
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(line);
        if (yearMatcher.find()) {
            String year = yearMatcher.group();
            return new DateRange(year, year);
        }
        return null;
    }

    private boolean containsDate(String line) {
        return DATE_RANGE_PATTERN.matcher(line).find() || YEAR_PATTERN.matcher(line).find();
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (String line : normalizeText(text).split("\n")) {
            String cleaned = line.trim();
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private boolean isBulletLine(String line) {
        return BULLET_PATTERN.matcher(line.trim()).find();
    }

    private String cleanBullet(String line) {
        return BULLET_PATTERN.matcher(line.trim()).replaceFirst("").trim();
    }

    private String buildUnmatchedText(Map<String, String> sections) {
        StringBuilder unmatchedText = new StringBuilder();

        // Conservative source fallback: heuristic fields do not prove that every part
        // of a recognized section was understood. Keep that section available too.
        for (Map.Entry<String, String> section : sections.entrySet()) {
            if (!section.getKey().equals("summary") && !section.getValue().isBlank()) {
                unmatchedText
                        .append(section.getKey().toUpperCase())
                        .append("\n")
                        .append(section.getValue().trim())
                        .append("\n\n");
            }
        }

        return blankToNull(unmatchedText.toString().trim());
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private String firstNonBlank(String currentValue, String candidate) {
        return currentValue == null || currentValue.isBlank() ? blankToNull(candidate) : currentValue;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record DateRange(String startDate, String endDate) {
    }

    private enum EntryType { EXPERIENCE, PROJECT, EDUCATION }
}
