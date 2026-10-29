# MVP verification — 29 October 2026

- Backend: `mvnw.cmd test package` — **35 tests run, 0 failures, 0 errors, 1 skipped**, executable Spring Boot JAR built. The skipped check verifies PostgreSQL-specific column types; H2 cannot satisfy its PostgreSQL assumption.
- Frontend: `npm test` — **9 passed** (Vitest 5.0.5).
- Frontend: `npm run build` — **passed**, TypeScript and Vite 8.3.5 production bundle.
- Browser: `npm run test:e2e` — **1 passed**, Chromium desktop 1440×1000 and mobile 390×844, real backend HTTP calls, no API mocking. Verified registration/login/logout, generated valid PDF upload, editing contact/summary/nested responsibilities, save + fresh GET, browser refresh, history after new login, raw source inspection, mobile width, owner-only history, cross-user GET/PUT 404 and invalid-token 401.
- `npm audit` after updating the test/build tools — **0 vulnerabilities**.
- `git diff --check` — passed.

Runtime verification used the actual Spring Boot application with its test-only H2 dependency because Docker CLI is installed but no Docker daemon/Desktop is available on this machine. PostgreSQL startup and persistence were **not verified live** here. The standard documented local setup uses PostgreSQL 16. No production backend settings were changed for H2.

For reproducing this temporary browser-test backend without Docker, from `backend` (PowerShell):

```powershell
.\mvnw.cmd org.springframework.boot:spring-boot-maven-plugin:4.0.6:run '-Dspring-boot.run.useTestClasspath=true' '-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:mvp-browser --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect --app.jwt.secret=local-browser-test-secret-at-least-32-characters --spring.jpa.show-sql=false'
```

Use `./mvnw` on macOS/Linux. This is a disposable **in-memory** database: accounts and CVs disappear when the backend stops. Use PostgreSQL for ongoing personal testing. Start Vite separately as in [the local guide](LOCAL-MVP.md).

The real HTTP test exposed an existing servlet error-dispatch issue: controller 404/400 responses could be converted to 401 by a second security pass. The minimal ERROR-dispatch allowance in SecurityConfig preserves those status codes without permitting direct unauthenticated API or `/error` requests.

Screenshots use only a generated fictitious CV:

- [Login](screenshots/login.png)
- [Desktop workspace](screenshots/workspace.png)
- [Mobile profile](screenshots/mobile.png)

The initial local staged README notes are preserved in `PREVIOUS-DEVELOPMENT-STATUS.md`; their development-status claims are historical, not current feature documentation.
