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

## Saved resume profiles

Uploads now persist the parsed profile as JSON in nullable SQL `TEXT` column
`resumes.parsed_profile`, using a JPA converter and the existing Jackson library.
`resumes.parsing_error` stores the safe parsing-failure message. No new dependencies
or external AI services are required. The upload response shape is unchanged.

All resume routes require `Authorization: Bearer <jwt>`. Ownership is derived from
the authenticated JWT subject and enforced in the repository query. Both a missing
resume and another user's resume return **404**. Missing, expired or invalid JWTs
return **401**; no role-based 403 is needed for these owner-only endpoints.

- `GET /api/resumes/{id}` returns the same envelope as upload, including the latest
  `parsedProfile`, immutable `extractedText`, metadata and `parsingError`.
- `PUT /api/resumes/{id}/profile` accepts the profile object directly (not the
  envelope) and performs a **full replacement**, returning the updated envelope.
  Omitted scalar fields become null; omitted top-level lists become empty lists.
  Explicit null lists, empty lists and null/empty scalar values are supported.
  A null request body is rejected. Saving a corrected profile clears `parsingError`.
  Neither ownership, filename, upload time nor `extractedText` can be edited.
  `originalText` and `unmatchedText` inside the profile are editable annotations;
  the exact PDF source remains available as `extractedText`.
- Invalid JSON, wrong field types, invalid email and validation failures return
  **400**. String limits: 1,000 characters for metadata, 200,000 for summary/source
  text, 10,000 per list string. Lists allow at most 500 strings or 100 nested
  entries. Null list elements are rejected; nested validation is recursive.
  Dates remain free-form strings, as in the parser contract. Both `jobTitle` and
  legacy `jobTittle` remain supported independently.

Example (save this object as `profile.json`; it replaces all previous fields):

```json
{
  "fullName": "Jordan Lee",
  "email": "jordan@example.com",
  "summary": "Backend engineer",
  "skills": ["Java", "SQL"],
  "education": [{"institution": "Example University", "degree": "BSc"}],
  "experience": [{"company": "Example Inc", "jobTitle": "Engineer", "responsibilities": ["Built APIs"]}],
  "projects": [{"name": "ApplyPilot", "technologies": ["Java"], "description": ["Resume tools"]}],
  "originalText": null,
  "unmatchedText": "Awards"
}
```

```sh
curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/resumes/1
curl -X PUT -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  --data-binary @profile.json http://localhost:8080/api/resumes/1/profile
```

### Migration and verification

The existing `spring.jpa.hibernate.ddl-auto=update` configuration adds these nullable
columns without deleting rows. For environments managing schema changes explicitly,
apply this additive PostgreSQL SQL before deploying (no backfill required):

```sql
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS parsed_profile TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS parsing_error VARCHAR(200);
```

Existing rows return `parsedProfile: null` and `parsingError: null`; they can be
edited immediately. They are not automatically re-parsed. Parsing failures on new
uploads retain text and metadata and the safe error message across later GETs.
A database write failure is an API failure, not a successful profile save.

Run `cd backend && ./mvnw clean test` (Windows: `mvnw.cmd clean test`) with Java 21.
Tests use H2, real JWT authentication, PDFBox and JPA conversion. Integration tests
verify upload/reload, parse-failure recovery, nested replacement, source preservation,
multiple resumes, legacy rows, ownership, invalid tokens and malformed input.
CI runs the same full suite for PRs against main.

Limitations: persistence is tested with H2 rather than a live PostgreSQL instance;
JSON fields are not indexed for queries. Concurrent full replacements use last-write
wins; there is no edit history or optimistic locking. Rule-based parsing limitations
above still apply. This change includes no frontend.
