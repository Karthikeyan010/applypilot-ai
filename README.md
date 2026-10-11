# applypilot-ai
AI-powered job application tracker and resume-job matching platform.

## Rule-based resume extraction

Authenticated users can upload a PDF resume with:

```http
POST /api/resumes/upload
Content-Type: multipart/form-data
Authorization: Bearer <jwt>

file=<resume.pdf>
```

The backend extracts the PDF text with PDFBox, stores the complete source text in
`Resume.extractedText`, and returns both the original text and a best-effort
structured profile. The parser is intentionally rule-based and does not call
external AI APIs, train models, or claim perfect accuracy.

Example response shape:

```json
{
  "id": 1,
  "userId": 42,
  "fileName": "resume.pdf",
  "extractedText": "Jordan Lee\n...",
  "parsedProfile": {
    "fullName": "Jordan Lee",
    "email": "jordan@example.com",
    "phone": "+1 555 010 1234",
    "location": "Austin, TX",
    "summary": "Backend engineer focused on job application workflows.",
    "skills": ["Java", "SQL", "Python", "Spring Boot"],
    "education": [
      {
        "institution": "University of Texas",
        "degree": "MSc in Computer Science",
        "fieldOfStudy": "Computer Science",
        "startDate": "2017",
        "endDate": "2019",
        "location": "Austin, TX",
        "originalText": "University of Texas | MSc in Computer Science | 2017 - 2019 | Austin, TX"
      }
    ],
    "experience": [
      {
        "company": "Northstar Apps",
        "jobTitle": "Senior Backend Engineer",
        "jobTittle": "Senior Backend Engineer",
        "startDate": "Jan 2022",
        "endDate": "Present",
        "location": "Austin, TX",
        "responsibilities": ["Built resume upload APIs with PDF text extraction."],
        "originalText": "Senior Backend Engineer | Northstar Apps | Austin, TX | Jan 2022 - Present\n- Built resume upload APIs with PDF text extraction."
      }
    ],
    "projects": [
      {
        "name": "ApplyPilot Matcher",
        "technologies": ["Java", "Spring Boot", "PostgreSQL"],
        "description": ["Created rule-based matching reports."],
        "originalText": "ApplyPilot Matcher | Java, Spring Boot, PostgreSQL\n- Created rule-based matching reports."
      }
    ],
    "originalText": "Jordan Lee\n...",
    "unmatchedText": "HEADER\nJordan Lee\n..."
  },
  "parsingError": null,
  "uploadedAt": "2026-10-11T10:15:30"
}
```

Supported formats are simple, ATS-friendly text resumes with recognizable
headings such as `Summary`, `Skills`, `Experience`, `Projects`, and `Education`.
Multiple experience, project, and education entries are supported when entries
use common heading/date/bullet patterns, especially pipe-delimited metadata such
as `Role | Company | Location | Jan 2022 - Present`.
Multiline role/company/date and institution/degree/date headers are also
supported, including adjacent entries without blank lines. Project metadata may
use `Technologies:`, `Tech stack:`, or `Tools:`.

If structured parsing fails after persistence, the upload still returns HTTP 200
with its saved ID and complete `extractedText`, `parsedProfile: null`, and
`parsingError: "Resume saved, but structured profile extraction failed."`.
Successful parsing returns `parsingError: null`.

Limitations:

- The parser avoids fabricating uncertain values; missing or ambiguous fields are
  returned as `null` or empty lists.
- Descriptive bullets and each entry's `originalText` are preserved so users can
  correct extraction results later.
- `parsedProfile.originalText` preserves the exact source string, including line
  endings and whitespace. Entry `originalText` uses trimmed lines and LF endings.
- `unmatchedText` is a conservative source fallback: it includes the complete
  content of recognized sections as well as awards and certifications, except
  summary content already exposed as `summary`. It can duplicate structured
  fields; this deliberately keeps uncertain metadata and unexpected lines
  inspectable instead of claiming every part of a section was parsed.
- Certifications are not yet represented as first-class DTOs and should be added
  in a follow-up issue when the response model is expanded.
  Issue #7 explicitly allows this deferral; certification source text remains
  available in `originalText` and `unmatchedText`.

Backend verification (from `backend` on Windows): `./mvnw.cmd test`.
The Spring Boot context test uses a test-only H2 database and an explicit test JWT
secret. Surefire starts Mockito's agent explicitly to avoid dynamic agent
attachment; no external database or AI service is required for the tests.
