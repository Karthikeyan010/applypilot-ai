## Development Status

ApplyPilot is currently under active development.

### Completed

- User registration and login
- JWT-based stateless authentication
- Protected API endpoints
- PostgreSQL persistence
- PDF resume upload
- Resume text extraction using Apache PDFBox
- Resume persistence linked to the authenticated user
- Safe resume API responses using DTOs
- Structured resume response DTOs for:
    - contact information
    - education
    - experience
    - projects
- `ResumeExtractionService` abstraction
- Rule-based resume extraction implementation
- Resume section detection
- Contact information extraction
- Unit tests for resume section and contact extraction

### In Progress

- Skills extraction
- Structured resume parsing

### Next

- Date-range parsing
- Education extraction
- Experience extraction
- Project extraction
- Combine extractors into `RuleBasedResumeExtractionService`
- End-to-end structured resume extraction testing
- User review and confirmation of extracted information
- Persistent profile and Project Library
- Job-description and resume matching

### Resume Processing Architecture

```text
Resume PDF
    ↓
PDFBox
    ↓
Raw Resume Text
    ↓
SectionExtractor
    ↓
Specialised Extractors
    ├── ContactInfoExtractor
    ├── SkillsExtractor
    ├── EducationExtractor
    ├── ExperienceExtractor
    └── ProjectExtractor
    ↓
RuleBasedResumeExtractionService
    ↓
ResumeProfileResponse
    ↓
User Review & Confirmation