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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuleBasedResumeExtractionService implements ResumeExtractionService {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?i)\\b((?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+\\d{4}|\\d{4})\\s*(?:-|\\u2013|\\u2014|to)\\s*((?:present|current|now)|(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+\\d{4}|\\d{4})\\b"
    );
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(?:19|20)\\d{2}\\b");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[\\-\\u2022*]\\s*");
    private static final Pattern DEGREE_PATTERN = Pattern.compile(
            "(?i)\\b(B\\.?Sc\\.?|M\\.?Sc\\.?|B\\.?A\\.?|M\\.?A\\.?|B\\.?Eng\\.?|M\\.?Eng\\.?|Bachelor|Master|MBA|PhD|Doctorate|Diploma|Certificate)\\b"
    );

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
                .originalText(normalizedText)
                .unmatchedText(buildUnmatchedText(sections))
                .skills(skillsExtractor.extract(sections.get("skills")))
                .experience(parseExperience(sections.get("experience")))
                .projects(parseProjects(sections.get("projects")))
                .education(parseEducation(sections.get("education")))
                .build();
    }

    private List<ExperienceDto> parseExperience(String experienceText) {
        return splitEntries(experienceText, this::isExperienceStart).stream()
                .map(this::parseExperienceEntry)
                .toList();
    }

    private ExperienceDto parseExperienceEntry(List<String> lines) {
        String originalText = String.join("\n", lines);
        List<String> headerLines = lines.stream()
                .filter(line -> !isBulletLine(line))
                .filter(line -> !isDescriptionContinuation(line))
                .takeWhile(line -> responsibilitiesHaveNotStarted(lines, line))
                .toList();
        List<String> responsibilities = lines.stream()
                .filter(line -> !headerLines.contains(line))
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

    private boolean responsibilitiesHaveNotStarted(List<String> lines, String candidate) {
        int candidateIndex = lines.indexOf(candidate);
        for (int index = 0; index < candidateIndex; index++) {
            if (isBulletLine(lines.get(index))) {
                return false;
            }
        }
        return true;
    }

    private List<ProjectDto> parseProjects(String projectsText) {
        return splitEntries(projectsText, this::isProjectStart).stream()
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
            if (line.toLowerCase().startsWith("technologies:")
                    || line.toLowerCase().startsWith("tech stack:")
                    || line.toLowerCase().startsWith("tools:")) {
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
        return splitEntries(educationText, this::isEducationStart).stream()
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

        for (String part : splitMetadata(lines)) {
            DateRange dateRange = extractDateRange(part);
            if (dateRange != null) {
                startDate = firstNonBlank(startDate, dateRange.startDate());
                endDate = firstNonBlank(endDate, dateRange.endDate());
                continue;
            }

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

    private List<List<String>> splitEntries(String sectionText, Predicate<String> startPredicate) {
        List<List<String>> entries = new ArrayList<>();
        List<String> current = new ArrayList<>();
        boolean currentHasDescription = false;

        for (String line : lines(sectionText)) {
            boolean startsNewEntry = !current.isEmpty()
                    && startPredicate.test(line)
                    && (containsDate(line) || currentHasDescription || current.size() >= 2);

            if (startsNewEntry) {
                entries.add(current);
                current = new ArrayList<>();
                currentHasDescription = false;
            }

            current.add(line);
            if (isBulletLine(line)) {
                currentHasDescription = true;
            }
        }

        if (!current.isEmpty()) {
            entries.add(current);
        }

        return entries;
    }

    private boolean isExperienceStart(String line) {
        return !isBulletLine(line) && !isDescriptionContinuation(line);
    }

    private boolean isProjectStart(String line) {
        return !isBulletLine(line)
                && !line.toLowerCase().startsWith("technologies:")
                && !line.toLowerCase().startsWith("tech stack:");
    }

    private boolean isEducationStart(String line) {
        return !isBulletLine(line);
    }

    private boolean isDescriptionContinuation(String line) {
        String lowerCaseLine = line.toLowerCase();
        return lowerCaseLine.startsWith("responsibilities:")
                || lowerCaseLine.startsWith("achievements:")
                || lowerCaseLine.startsWith("description:");
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
        Set<String> structuredSections = Set.of("skills", "experience", "projects", "education");
        StringBuilder unmatchedText = new StringBuilder();

        for (Map.Entry<String, String> section : sections.entrySet()) {
            if (!structuredSections.contains(section.getKey()) && !section.getValue().isBlank()) {
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
}
