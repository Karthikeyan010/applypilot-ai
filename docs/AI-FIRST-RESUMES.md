# AI-first resume import (issue #18)

This branch depends on draft PR #16 (`feat/frontend-mvp`). The new parser is automatic **Ollama-only**; it never invokes the legacy rule-based CV parser. There is no paid API, external API key, custom training, or cloud fallback. Uploading a PDF sends its extracted text to your administrator-configured Ollama endpoint. With the defaults, it stays on localhost. A private-server URL deliberately sends it to that server.

## Windows local setup

Prerequisites: Java 21, Node.js 24, Docker running Linux containers for persistent PostgreSQL, and [Ollama for Windows](https://docs.ollama.com/windows). Use the normal Ollama installer; restart your terminal after installation. See the [base local guide](LOCAL-MVP.md) for database troubleshooting and macOS/Linux commands.

In PowerShell:

```powershell
ollama --version
ollama pull qwen3:4b
# The desktop installation normally starts Ollama automatically.
# If it is not running, keep a separate terminal open with:
ollama serve
```

Do not start a second `ollama serve` if port 11434 is already occupied by Ollama. Check the installed model:

```powershell
Invoke-RestMethod http://127.0.0.1:11434/api/tags
```

From the repository root:

```powershell
docker compose up -d postgres
```

Backend terminal, from the repository root:

```powershell
$env:DB_PASSWORD = 'postgres'
$env:JWT_SECRET = 'local-development-only-change-this-secret-at-least-32-characters'
$env:OLLAMA_BASE_URL = 'http://127.0.0.1:11434'
$env:OLLAMA_MODEL = 'qwen3:4b'
$env:OLLAMA_TIMEOUT_SECONDS = '120'
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend terminal, from the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173**. Register/sign in and upload a text-based PDF. Expect a loading indicator while the local model loads and generates the draft. Only select Save changes after reviewing the draft and checking the confirmation box.

If you already run another backend on 8080, stop that instance yourself or add `'-Dspring-boot.run.arguments=--server.port=8081'` to the Maven startup command. In the frontend terminal set `$env:API_PROXY_TARGET='http://127.0.0.1:8081'` before starting Vite. Use `npm run dev -- --port 5180` and open `http://127.0.0.1:5180` if 5173 is occupied. These are the isolated ports used during this implementation's browser verification.

### Memory and model selection

The [qwen3:4b model listing](https://ollama.com/library/qwen3:4b) reports roughly 2.5 GB for its quantized download. **Download size is not runtime memory.** As a planning estimate, allow at least 8 GB system RAM for a small-model experiment and prefer **16 GB or more** for the default 4B model plus a 16K context, Windows, Java, browser and PostgreSQL. More context/concurrency requires more RAM/VRAM. CPU inference works but can take minutes; a supported GPU improves latency. These are planning estimates, not measured minimums or a performance guarantee. Review [Ollama's FAQ](https://docs.ollama.com/faq) and `ollama ps` on your hardware.

On constrained hardware you can explicitly choose a smaller model, for example `ollama pull qwen3:1.7b`, then set `OLLAMA_MODEL=qwen3:1.7b`. Smaller models can make more grouping mistakes. The application never downloads/switches a model automatically. Initial model downloads require internet; inference with an installed local model does not need an external API.

### Configuration

| Variable | Default | Meaning |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Server base URL; `/api/chat` is appended. HTTP(S), no query/userinfo; no redirects followed. Server configuration only, never taken from an uploaded CV/request. |
| `OLLAMA_MODEL` | `qwen3:4b` | Exact preinstalled local model name; no automatic paid/cloud fallback. |
| `OLLAMA_TIMEOUT_SECONDS` | `120` | Overall request/body deadline, 1–600 seconds. Connection timeout is 5 seconds. |
| `OLLAMA_MAX_SOURCE_CHARACTERS` | `16000` | Reject over-budget input without truncation (1,024–50,000). Must fit the chosen context including prompt and output. |
| `OLLAMA_CONTEXT_TOKENS` | `16384` | Context requested from Ollama, 4,096–32,768 tokens. Larger settings need more memory. |

Characters are not tokens. A 16,000-character CV plus the schema/prompt and output may still exceed the model's context, particularly for non-English text or a very verbose CV. Set a smaller input budget when reducing context. Truncated/incomplete output is rejected; no partial draft is committed. There is one inference request per backend instance at a time; other requests receive a retryable busy message. Output size is capped at 2 MB, JSON/list/string limits are checked locally, and unsupported response types/additional fields are rejected.

## Architecture and safety of saved data

```text
PDF -> PDFBox -> persisted immutable extractedText
                         |
                   ResumeParserService
                         |
              Ollama /api/chat + JSON schema
                         |
            strict schema/DTO validation + source checks
                         |
       extractionDraft + review warnings (separate SQL TEXT)
                         |
             editable draft -> explicit user confirmation
                         |
       PUT /profile -> parsedProfile + profileConfirmedAt
                         |
                 GET -> verified persistence
```

- `ResumeParserService` is the provider-independent contract. `OllamaResumeParserService` handles wire transport, schema-constrained output and validation. Adding a private provider later requires a new implementation of the interface, not changing controllers/editor DTOs.
- JSON schema describes the current contact/summary/skills/education/experience/project DTOs. It is sent in Ollama's `format` and prompt as [documented for structured outputs](https://docs.ollama.com/capabilities/structured-outputs). The server also checks the same schema subset (objects, required fields, additional properties, arrays, types, max sizes) before mapping to DTOs and running Jakarta validation. It rejects coercion, duplicate keys, trailing tokens, truncation and incomplete responses.
- Prompts explicitly group each named project plus all its technologies and bullets into one entry; the same applies to each employer/role. Wrapped lines stay inside a bullet. Model output remains unverified even when valid JSON.
- Source validation removes scalar/list values without a normalized matching source passage and records field-path warnings. It accepts whitespace/bullet differences but is intentionally conservative about paraphrasing. Empty/null fields plus the general review warning indicate uncertainty. This is **evidence matching, not semantic verification**: it cannot establish that a bullet belongs to the right project, resolve columns, verify chronology or prove that source text is true.
- `originalText` and `unmatchedText` are server-set to the complete exact extracted text for AI drafts; unmatched text is a conservative full-source fallback and can duplicate structured content. Entry source passages are checked too. No omission is silently claimed to be fully accounted for.
- Upload/retry writes only `extractionDraft` and `parsingError`. The repository uses an owner-scoped partial update so an in-flight extraction cannot overwrite a saved profile, even if confirmation occurred while inference was running. No master-profile fields are written by the provider.
- Existing saved profiles stay selected after retry. **Review AI draft instead** changes only the editor; confirming/saving it explicitly replaces the saved profile. Unsaved changes trigger a discard prompt. Each edit resets confirmation. Successful saves clear the pending draft and are reloaded with GET.
- `profileConfirmedAt` is set only on an explicit profile save. Legacy saved profiles have a null timestamp and are shown as awaiting review, rather than being retroactively labelled user-verified.
- CV contents/provider responses/JWTs are not logged by this integration. Safe fixed provider errors offer retry/manual entry. CV text is treated as untrusted model input, not instructions. This reduces prompt-injection exposure but does not make extraction infallible.
- Existing JWT ownership enforcement, response DTOs, immutable source and full replacement profile shape remain compatible with PR #16. Legacy `jobTittle` is preserved independently in saved/editable data. The AI prompt leaves it null unless separately present in the source.

### Routes

All require authentication and owner checks:

- `POST /api/resumes/upload`: existing multipart `file`. Persists PDF text, attempts AI extraction automatically, returns the existing envelope plus `extractionDraft` and `profileConfirmedAt`. For new uploads `parsedProfile` remains null until the user saves. A saved upload with unavailable AI is HTTP 200 with `parsingError`, not a falsely successful parse.
- `POST /api/resumes/{id}/extraction`: retry/new draft. Does not change `parsedProfile` or its confirmation timestamp. Ownership is checked before inference. Failure is reflected in `parsingError` with the saved profile unchanged.
- `POST /api/resumes/manual`: `{ "name": "My career profile" }`, creates a blank profile envelope without a file or inference. Edit and confirm using the existing PUT route.
- `PUT /api/resumes/{id}/profile`: unchanged full replacement DTO; explicit user action in the UI. Clears draft/error and sets confirmation time. Programmatic callers are responsible for obtaining user confirmation before calling it.
- `GET /api/resumes` now also reports `hasDraft` metadata; GET by ID includes pending draft/warnings. Drafts are owner-only resume data.

Schema migration is additive; existing rows are not re-parsed. Hibernate update adds nullable columns locally. For managed PostgreSQL migrations:

```sql
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS extraction_draft TEXT;
ALTER TABLE resumes ADD COLUMN IF NOT EXISTS profile_confirmed_at TIMESTAMP;
```

Back up data before applying changes to a real server. The existing saved profile format and source columns do not change.

## Editor and browser walkthrough

Two generated, non-personal PDFs are included: [standard](fixtures/standard.pdf) and [wrapped/multiline](fixtures/wrapped.pdf). Text sources live in `backend/src/test/resources/cvs/`. Their named bookstore project has three bullets; the Example Co role has three responsibilities. The wrapped layout introduces extra whitespace, line wrapping and different bullet/date formatting. No real user's CV is in these fixtures.

1. Upload each PDF into a new account while real Ollama is running. Check **Unverified AI draft**, one bookstore project, its three bullets and technologies, and one Example Co experience entry with three responsibilities. Inspect source/unmatched text and warnings. Any model errors should be corrected manually and recorded with the model tag and Ollama version.
2. Edit a description with several lines. It must stay **one bullet**. Use Add bullet / Remove bullet and bullet ordering controls. Entry cards can collapse/expand.
3. Add another project with the same name and another bullet. Choose **Merge with previous**. All list items and original source passages remain; differing non-empty scalar values are joined visibly with ` / ` for review, rather than silently discarded.
4. Choose **Split from this bullet** to move that bullet and subsequent ones into a new entry. Metadata/source is copied so nothing disappears; edit the duplicate title/metadata if it represents a distinct project. Reorder entries with Move up/down.
5. Save is disabled until you check **I reviewed these details and confirm saving this profile**. Any subsequent edit clears that checkbox. Save, refresh the browser, reselect the CV, and confirm multiline bullets, technologies, legacy fields and source text remain.
6. Retry AI extraction on this saved profile. Your saved edits must remain visible and persist unchanged. Only **Review AI draft instead**, followed by confirmation/save, can replace them.
7. Stop Ollama (quit the tray application), or temporarily point `OLLAMA_BASE_URL` to an unused local port and restart the backend. Upload/retry: expect an Ollama error, retry action and **Manual profile entry**. Your saved profile must remain unchanged; there must be no rule-based fallback result.
8. Choose **Create profile without a PDF**. Add contact details, summary, skills, education, multiple project/experience bullets, and confirm/save. No inference request is needed.
9. Check the mobile viewport and keyboard navigation; details summaries and all actions are labelled. Log out and sign in again to find saved profiles and pending drafts in the library. A second user's list/read/retry/save must not reveal or modify them.

## Tests and reproducible verification

Normal checks (from repository root):

```powershell
cd backend
.\mvnw.cmd clean test package
cd ../frontend
npm ci
npm test
npm run build
npx playwright install chromium
```

With the normal backend + Vite + **real Ollama** started as above, run `npm run test:e2e` from `frontend` with `OLLAMA_TEST_FIXTURE` unset. This runs the two grouping/draft round trips and manual-creation test against the installed model. The controlled provider-failure test is skipped in live mode. Model-dependent assertions intentionally fail if the chosen model loses bullets/grouping. They are a small smoke test, not a quality benchmark. Tests create synthetic accounts/records in the configured database.

For deterministic **contract tests**, no model download is required. Start a separate backend pointed explicitly at `http://127.0.0.1:11999` with `OLLAMA_MODEL=synthetic-model`. Run the normal frontend, then from `frontend`:

```powershell
$env:OLLAMA_TEST_FIXTURE='1'
npm run test:e2e
```

Playwright starts `e2e/ollama-fixture-server.mjs`, which returns labelled synthetic JSON and controllable 503 responses. There are **no mocked application APIs**; browser -> Vite -> Spring -> PDFBox/JPA/JWT -> HTTP provider/schema/evidence pipeline is real. This proves transport, editor, confirmation, ownership and persistence contracts, **not model extraction accuracy**. The fixture server refuses to start without the explicit test environment variable and is never a production parser.

A disposable H2 backend for these contract tests (from `backend`) can be started without Docker:

```powershell
.\mvnw.cmd org.springframework.boot:spring-boot-maven-plugin:4.0.6:run '-Dspring-boot.run.useTestClasspath=true' '-Dspring-boot.run.arguments=--server.port=8081 --spring.datasource.url=jdbc:h2:mem:issue18-browser --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect --app.jwt.secret=local-browser-test-secret-at-least-32-characters --spring.jpa.show-sql=false --app.ollama.base-url=http://127.0.0.1:11999 --app.ollama.model=synthetic-model --app.ollama.timeout-seconds=5'
```

In the frontend terminal set `$env:API_PROXY_TARGET='http://127.0.0.1:8081'`, run `npm run dev -- --port 5180`. In the test terminal additionally set `$env:FRONTEND_TEST_URL='http://127.0.0.1:5180'`. **All H2 accounts/drafts disappear when that backend stops.** Stop the test processes and clear test environment variables before personal-CV testing. The 11999 fixture should never receive a real CV.

Backend unit/wire tests use a local synthetic HTTP server: schema requests, both source layouts, unsupported-value removal, malformed/truncated/invalid JSON, missing/wrong fields, unavailable model, oversized input and body-read deadline. Security/JPA tests verify drafts survive reload, retries never modify saved/legacy/source fields, manual profiles and owner isolation. Old rule-based tests remain historical regressions, but a Spring-context assertion ensures the old parser is not a runtime bean. Frontend tests cover multiline edits/merge/split/reorder, full data preservation, auth/errors and confirmation reset.

The dependent PR's backend/frontend/browser CI also targets `feat/frontend-mvp`; PR #16 need not be merged first. See [recorded outcomes](ISSUE18-VERIFICATION.md) for the tests actually run here, screenshots and explicit omissions.

## Tika / DOCX evaluation

[Apache Tika's OOXMLParser](https://tika.apache.org/3.2.3/formats.html#Microsoft_Office_document_formats) extracts DOCX text using Apache POI. It is a suitable future **text extraction** adapter upstream of `ResumeParserService`; it is not a structured resume parser and would not replace Ollama. Tika's [download page](https://tika.apache.org/download.html) lists maintained release lines; pin a current patched release and audit its transitive POI/XML/compression dependencies when implementing the adapter.

**Decision for this PR: keep PDFBox and PDF-only upload. DOCX is not enabled or advertised.** Adding Tika's Office module now would expand accepted archive/XML inputs and transitive dependencies without the needed adversarial-document tests. A follow-up adapter must allowlist DOCX MIME/extension plus actual package type, disable embedded-document extraction and external entity/resource resolution, enforce compressed/uncompressed/entry/output/time limits, and test malformed archives, zip bombs, tables, headers, numbering and embedded objects. Use the minimal Office parser module, not Tika's full standard parser bundle. Both adapters must preserve source text and use the same AI draft/confirmation pipeline. Existing non-PDF uploads continue to return 400.

## Future private inference server

Run the same chosen model on a self-hosted Ollama host. Pull the model there, keep it private, and set only the Spring backend's `OLLAMA_BASE_URL` and `OLLAMA_MODEL` (for example `https://inference.internal.example` and the same pinned tag). The browser talks only to Spring; no Ollama URL, model credentials or provider calls go into Vite.

Bind Ollama's listener via its documented `OLLAMA_HOST` setting only inside a trusted private network. Use firewall rules/private networking, TLS termination and authenticated network access appropriate to your deployment; Ollama's local API is not a public multi-tenant auth boundary. This implementation has no paid API key configuration. A private reverse proxy can supply infrastructure authentication; URL userinfo/query credentials are deliberately rejected. Keep the proxy timeout above `OLLAMA_TIMEOUT_SECONDS`. Do not expose port 11434 publicly or enable unrestricted CORS for browser access.

For a future multi-user deployment, add durable asynchronous jobs, per-user quotas/rate limits, global concurrency controls across backend replicas, revision-based confirmation conflicts, encrypted storage/backups, model/prompt version tracking, a larger synthetic accuracy evaluation corpus and a retention policy for drafts/source. Current inference is synchronous, one slot per backend process, and manual saves still use PR #16's full-replacement/last-write-wins semantics. These are documented limits, not production-readiness claims.
