# Backend CI

GitHub Actions runs the full backend test suite on pull requests targeting `main`
and pushes to `main`, using Ubuntu, Temurin Java 21, and cached Maven dependencies.
From `backend`, run `./mvnw clean test` (Windows: `.\mvnw.cmd clean test`).

Test-only H2 and JWT settings live in
`backend/src/test/resources/application.properties`; no PostgreSQL service or
production secrets are required. H2 is a test-scoped dependency. Surefire loads
the Mockito agent at startup and keeps PDFBox's test font cache under `target`.
All tests run without test filters, skip flags, or changes to test assertions.

Maven failures are shown in the workflow logs and PR Checks. The workflow uses
only read-only contents permissions and does not deploy or merge changes.
Actions are pinned to verified release commit SHAs.

Adding a workflow does not start a run on an unchanged existing PR. After merging
the CI workflow through review, synchronize the resume extraction PR with `main`
or push a new commit to trigger its `pull_request` check.
