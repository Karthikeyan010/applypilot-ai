# Issue #18 verification — 30 October 2026

Branch: `codex/ai-resume-parser`, based on draft PR #16 (`feat/frontend-mvp`). The dependent draft PR targets that branch; nothing is merged automatically.

## Actual results

| Check | Result |
|---|---|
| Backend `mvnw.cmd clean test package` / final `mvnw.cmd test package` | **42 run, 41 passed, 1 skipped, 0 failures/errors; executable JAR built** |
| Frontend `npm test` | **13 passed** across app and entry-editor tests |
| Frontend `npm run build` | **Passed** TypeScript + Vite production build |
| Chromium `npm run test:e2e` with explicit synthetic provider | **4 passed**; final run 15.9 seconds |
| `git diff --check` | **Passed** |

The skipped backend test checks PostgreSQL-specific column types and deliberately skips under H2. Browser tests use the real Spring application, PDFBox, HTTP client, schema validation, source-evidence checks, JWT and JPA/H2. Only the model server's output is synthetic; no application API is intercepted. This tests integration contracts and UI behavior, **not live LLM accuracy**.

Ollama is not installed on the execution machine. No live model inference, download-time, memory/latency benchmark or PostgreSQL runtime verification is claimed. The checked-in sources/PDFs and [live Ollama walkthrough](AI-FIRST-RESUMES.md) allow those checks on the user's own hardware. Backend tests require loopback networking for their local HTTP fixture. The first sandboxed run could not open Java's loopback selector; rerunning with loopback access passed.

## Browser checks performed

1. Standard layout: one named bookstore project with three description bullets and three technologies; one Example Co role with three responsibilities in an **unverified extraction draft**.
2. Wrapped layout: same data with differing whitespace, line wrapping, bullet styles and dates. Both variants pass through PDFBox before the actual provider HTTP/schema/evidence pipeline.
3. A synthetic unsupported phone value is removed and flagged, rather than presented as supported fact.
4. Add an extra manually reviewed project/bullet, merge with previous, edit a bullet with embedded newline, explicitly confirm, save, GET, refresh, select again and verify all four bullets and exact source/unmatched text persist.
5. Retry AI extraction on the confirmed profile; its manual edits stay unchanged while a separate new draft appears.
6. Create a profile without a PDF/inference, enter multiline bullets, confirm/save and reload.
7. Simulate provider 503; upload/source persists, retry/manual controls remain available, and retry failure never erases the saved manual profile.
8. Check 390×844 mobile width and inspect the editor screenshots. Unit tests additionally exercise split/reorder, conflicting scalar merges, preservation of legacy jobTittle, auth failures/session clearing and confirmation reset after editing.

## Root cause of the original split

The old `startsEntry` heuristic considers a non-bullet line a new project heading when there are technologies/descriptions already and the next line resembles metadata or a bullet. Its bullet recognizer only understands a small set (`-`, `•`, `*`), and its continuation recognizer uses a short list of leading verbs. An unfamiliar glyph such as `▪` followed by a recognized `-` bullet, or an unrecognized wrapped continuation, can therefore become a new project's name. The original editor then joins each description array into one newline-delimited textarea, making wrapped text and distinct bullets hard to distinguish.

The production controller now uses only `ResumeParserService`/Ollama, with explicit grouping instructions, schema/DTO validation, evidence warnings and confirmation. The old class remains as a historical regression reference, with its Spring service registration removed. A context test asserts that no legacy `ResumeExtractionService` bean exists. AI output can still group incorrectly; the editor's merge/split/reorder controls and review gate are necessary, not a claim of perfect extraction.

## Fixtures and screenshots

Only synthetic/non-personal CV data is included:

- [Standard PDF](fixtures/standard.pdf) and [wrapped PDF](fixtures/wrapped.pdf), generated from the checked-in text fixtures.
- [Desktop multiline editor](screenshots/issue18/standard-editor.png)
- [Mobile multiline editor](screenshots/issue18/standard-mobile.png)
- [Wrapped-layout desktop](screenshots/issue18/wrapped-editor.png)
- [Wrapped-layout mobile](screenshots/issue18/wrapped-mobile.png)

Screenshots show the synthetic provider contract journey, not a real-model accuracy demonstration.

## Limits and follow-up testing

- Run the live-model suite with Ollama and a downloaded model before relying on extraction accuracy. The two synthetic layouts are smoke fixtures, not a representative benchmark.
- Live PostgreSQL migration/startup remains to be verified on a Docker-enabled machine; current checks use H2.
- Tika/DOCX was evaluated and intentionally not enabled in this PDF-focused change; the adapter and archive-safety requirements are documented.
- Source substring matching cannot establish semantic correctness, bullet ownership or completeness. Unsupported paraphrases can be removed even when a human would consider them equivalent.
- Inference is synchronous and limited to one request per backend instance. Master profile saves retain the existing full-replacement/last-write-wins behavior; there is no undo history or multi-tab optimistic conflict resolution.
- Existing local-MVP sessionStorage auth and deployment limitations from PR #16 still apply.

See [AI-first setup, browser instructions and private-server deployment](AI-FIRST-RESUMES.md) for exact commands and configuration.
