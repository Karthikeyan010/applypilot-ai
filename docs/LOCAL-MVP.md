# Run the frontend MVP locally

> Updated parsing/editor behavior and Ollama setup: [AI-first resume guide](AI-FIRST-RESUMES.md). The original issue #15 walkthrough below describes the initial MVP; automatic imports now produce unverified drafts and require the review checkbox before save.

Requires Java 21, Node.js 24 (with npm), Git, and Docker Desktop / Docker Engine running Linux containers. No paid service or AI API key is needed.

## Windows (PowerShell)

From the repository root, start the database:

```powershell
docker compose up -d postgres
```

In terminal 1, from the repository root:

```powershell
$env:DB_PASSWORD = 'postgres'
$env:JWT_SECRET = 'local-development-only-change-this-secret-at-least-32-characters'
cd backend
.\mvnw.cmd spring-boot:run
```

In terminal 2, from the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173** in your browser. Keep both terminals running.

## macOS / Linux

From the repository root:

```sh
docker compose up -d postgres
cd backend
export DB_PASSWORD=postgres
export JWT_SECRET=local-development-only-change-this-secret-at-least-32-characters
./mvnw spring-boot:run
```

In a second terminal, from the repository root:

```sh
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173**. Use `bash mvnw` if your checkout lost the executable bit.

## Configuration and troubleshooting

- PostgreSQL: host `127.0.0.1`, port **5434**, database `applypilot_db`, user `applypilot_user`, password `postgres` (development-only Compose defaults). Data persists in Docker volume `applypilot_postgres_data`.
- Backend: port **8080**. Optional `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION` (milliseconds; default 86400000). Set variables in the backend terminal. JWT secret must be at least 32 bytes; use a private random value outside this local demonstration.
- Frontend: port **5173**; strict port prevents silently opening another address. Vite proxies relative `/api` requests to `http://127.0.0.1:8080`, so no permissive CORS changes are needed. Override with `API_PROXY_TARGET` in the frontend terminal if necessary. Never put backend secrets in `VITE_*` variables.
- First Maven/npm runs require internet to download dependencies. Docker must be running before `docker compose up`. Wait for the backend's `Started BackendApplication` message before signing in.
- Port already in use: stop the previous process, or change the backend port and `API_PROXY_TARGET` together. Frontend requests must go through the Vite server.
- Stop servers with Ctrl+C. Stop the database with `docker compose stop`. Avoid `docker compose down -v` unless you intend to erase saved accounts and CVs.
- `npm run build` creates `frontend/dist`; it is a build check, not a deployment. Production hosting needs an API reverse proxy and HTTPS.

## End-to-end walkthrough with your own CV

1. Open the frontend, choose **Create an account**, and use a unique email and password (at least 8 characters). Registration signs you in immediately.
2. Select **Upload a CV** and choose your own text-based PDF under 5 MB. A loading indicator covers upload and extraction; no invented percentage is displayed.
3. Check the filename/date and extraction state. A parser failure means the PDF was saved but structured extraction failed. A scanned PDF may yield no text; OCR is not available.
4. Review contact details, summary, skills, education, experience, and projects. Add/remove entries as needed. Lists use one item per line. The legacy job-title field appears when present and is independent of the current job title.
5. Expand **Raw extracted PDF text**, **Unmatched text / possible omissions**, and entry source text. Compare them with the PDF and note anything missed. These reference values remain unchanged on save.
6. Change a contact field, add an experience entry with two responsibilities, and select **Save changes**. The frontend sends the entire profile and immediately GETs it again. Only the verified response is shown as saved. Failed saves keep your edits.
7. Refresh the browser, select the CV in **Resume library**, and confirm your changes remain. Upload a second PDF and confirm newest-first history.
8. Log out (icon beside your name), then sign in again. Both PDFs should remain in the library. Protected information disappears on logout. Switching CVs/logging out with edits asks whether to discard them.
9. Create a second account using another browser profile or after logout. Its library should be empty; the backend rejects reads/writes for the first account's CV IDs with 404.
10. Try a non-PDF/empty/oversized file, an invalid email, a duplicate registration, a wrong password, and stopping the backend before Refresh. Verify useful errors and recovery. Restart the backend, then Refresh.
11. For expiry testing, start the backend with `JWT_EXPIRATION=60000`, log in, and wait one minute. You should return to sign-in with an expired-session message and no protected data.
12. Check a narrow mobile viewport and keyboard Tab/Shift+Tab/Enter navigation, including upload, source disclosures, and save.

Record feedback as: **screen/action → expected result → actual result → browser/viewport → screenshot**. Avoid including private CV text or credentials in public issues.

## Automated verification

```powershell
cd backend
.\mvnw.cmd clean test package
cd ../frontend
npm ci
npm test
npm run build
npx playwright install chromium
# With the normal backend/database and frontend already running:
npm run test:e2e
```

On macOS/Linux use `./mvnw clean test package`. Unit/UI tests mock HTTP to cover auth, history, upload/parsing failure, full replacement preservation, nested editing, save failure, network recovery, and session clearing. Backend integration tests use real JWT/security/JPA with H2, including history ownership, metadata safety and deterministic ordering. Playwright uses the running real APIs (no HTTP mocks) and creates unique test accounts with a generated non-personal PDF. It also checks cross-user reads/writes and saves screenshots in `docs/screenshots/`. It creates test records in the selected local database.

Both backend and frontend checks run in GitHub Actions. Browser tests are an explicit local check requiring running services.

## Security and MVP limits

The JWT and display identity are stored in **sessionStorage** so refresh works within the same tab; closing the tab ends the browser session. This is accessible to JavaScript and vulnerable to XSS, not production-grade auth. Tokens are sent only in Authorization headers, never in URLs/log messages, and removed on logout/401. Server-side JWT checks enforce access; client expiry checks are only a usability aid. Resume contents are kept in memory, not browser storage. A production service should use HTTPS, an HttpOnly secure cookie strategy, stronger auth error/status handling, rate limits and a deployment-specific security review.

Authentication errors from the existing backend can have generic statuses; the UI intentionally gives a safe credentials/unique-email message. Backend changes are limited to the missing list endpoint and allowing internal servlet ERROR dispatches so real HTTP 4xx statuses are preserved (direct requests remain protected). History is metadata-only and currently unpaginated. Profile updates are full replacement/last-write-wins, with no version history. Browser reload restores the library, then you select a CV. Only PDF text is stored, not downloadable PDF bytes. Source fields and untouched null/list/legacy values are preserved by cloning the loaded profile and patching only edited fields. Editing a list intentionally replaces that list using newline-separated values, including blank lines. Parser accuracy varies; unmatched text may duplicate extracted content.

Job descriptions, matching and applications are disabled **Coming soon** controls. No matching scores or application data are simulated.
