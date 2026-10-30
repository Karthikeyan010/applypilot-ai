import { test, expect, type Page } from "@playwright/test";
import { mkdir, readFile, writeFile } from "node:fs/promises";
// Synthetic text PDFs only, constructed with standard fonts; unicode bullet variants map to hyphens.
function pdf(source: string) {
  const safe = source.replace(/[•■–]/g, "-");
  const stream =
    "BT /F1 10 Tf 45 760 Td 14 TL " +
    safe
      .split(/\r?\n/)
      .map((line) => `(${line.replace(/([\\()])/g, "\\$1")}) Tj T*`)
      .join(" ") +
    " ET";
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
  ];
  let text = "%PDF-1.4\n";
  const offsets = [0];
  objects.forEach((o, i) => {
    offsets.push(Buffer.byteLength(text));
    text += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const start = Buffer.byteLength(text);
  text +=
    `xref\n0 6\n0000000000 65535 f \n` +
    offsets
      .slice(1)
      .map((n) => `${String(n).padStart(10, "0")} 00000 n \n`)
      .join("") +
    `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${start}\n%%EOF`;
  return Buffer.from(text);
}
async function register(page: Page) {
  const email = `ai-browser-${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`;
  await page.goto("/");
  await page.getByRole("button", { name: "Create an account" }).click();
  await page.getByLabel("Full name").fill("Jordan Lee");
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password").fill("Local-test-password-123");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(page.getByText("A fresh start")).toBeVisible();
}
async function token(page: Page) {
  return page.evaluate(
    () =>
      JSON.parse(sessionStorage.getItem("applypilot-session")!).token as string,
  );
}
for (const layout of ["standard", "wrapped"])
  test(`AI draft review, grouped bullets, merge, confirm, reload and retry: ${layout}`, async ({
    page,
    request,
  }) => {
    await register(page);
    const source = await readFile(
      `../backend/src/test/resources/cvs/${layout}.txt`,
      "utf8",
    );
    await page.getByLabel("Upload PDF").setInputFiles({
      name: `${layout}.pdf`,
      mimeType: "application/pdf",
      buffer: pdf(source),
    });
    await mkdir("../docs/fixtures", { recursive: true });
    await writeFile(`../docs/fixtures/${layout}.pdf`, pdf(source));
    await expect(
      page.getByText("Unverified AI draft", { exact: true }),
    ).toBeVisible({
      timeout: 150000,
    });
    await expect(
      page.getByLabel("Description bullet 3", { exact: true }),
    ).toHaveValue("Deployed services with Docker.");
    await expect(
      page.getByLabel("Responsibility 3", { exact: true }),
    ).toHaveValue("Improved automated tests.");
    await expect(
      page.getByRole("button", { name: "Save changes" }),
    ).toBeDisabled();
    const headers = { Authorization: `Bearer ${await token(page)}` };
    const rows = await (await request.get("/api/resumes", { headers })).json();
    const id = rows[0].id;
    const initial = await (
      await request.get(`/api/resumes/${id}`, { headers })
    ).json();
    expect(initial.parsedProfile).toBeNull();
    expect(initial.extractionDraft.profile.projects).toHaveLength(1);
    await page
      .getByLabel("Description bullet 2", { exact: true })
      .fill(
        "Added resilient checkout workflows.\nKept as one multiline bullet.",
      );
    await page
      .getByRole("button", { name: "Add project", exact: true })
      .click();
    await page
      .getByLabel("Project name")
      .last()
      .fill("Scalable Bookstore Microservices Platform");
    const entry = page.locator(".expandable-entry").last();
    await entry
      .getByRole("button", { name: "Add bullet", exact: true })
      .click();
    await entry
      .getByLabel("Description bullet 1", { exact: true })
      .fill("Extra manually reviewed bullet.");
    await page
      .getByRole("button", { name: "Merge projects 2 with previous" })
      .click();
    await expect(
      page.getByLabel("Description bullet 4", { exact: true }),
    ).toHaveValue("Extra manually reviewed bullet.");
    await page
      .getByLabel("I reviewed these details and confirm saving this profile.")
      .check();
    await page
      .getByRole("button", { name: "Save changes", exact: true })
      .click();
    await expect(
      page.getByText("Changes saved and reloaded from the server."),
    ).toBeVisible();
    const saved = await (
      await request.get(`/api/resumes/${id}`, { headers })
    ).json();
    expect(saved.extractionDraft).toBeNull();
    expect(saved.parsedProfile.projects).toHaveLength(1);
    expect(saved.parsedProfile.projects[0].description).toHaveLength(4);
    expect(saved.parsedProfile.originalText).toBe(initial.extractedText);
    expect(saved.parsedProfile.unmatchedText).toBe(initial.extractedText);
    await page.reload();
    await page
      .getByRole("button", { name: new RegExp(`${layout}.pdf`) })
      .click();
    await expect(
      page.getByLabel("Description bullet 4", { exact: true }),
    ).toHaveValue("Extra manually reviewed bullet.");
    await page.getByRole("button", { name: "Retry AI extraction" }).click();
    await expect(
      page.getByText(
        "New draft ready for review. Your saved profile is unchanged.",
      ),
    ).toBeVisible({ timeout: 150000 });
    await expect(
      page.getByLabel("Description bullet 4", { exact: true }),
    ).toHaveValue("Extra manually reviewed bullet.");
    const retried = await (
      await request.get(`/api/resumes/${id}`, { headers })
    ).json();
    expect(retried.parsedProfile).toEqual(saved.parsedProfile);
    expect(
      retried.extractionDraft.profile.projects[0].description,
    ).toHaveLength(3);
    await mkdir("../docs/screenshots/issue18", { recursive: true });
    await page
      .getByLabel("Description bullet 2", { exact: true })
      .scrollIntoViewIfNeeded();
    await page.screenshot({
      path: `../docs/screenshots/issue18/${layout}-editor.png`,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBeTruthy();
    await page.screenshot({
      path: `../docs/screenshots/issue18/${layout}-mobile.png`,
    });
  });
test("manual profile creation without inference", async ({ page }) => {
  await register(page);
  page.once("dialog", (dialog) => dialog.accept("Manual career profile"));
  await page
    .getByRole("button", { name: "Create profile without a PDF" })
    .click();
  await page.getByLabel("Full name", { exact: true }).fill("Manual Owner");
  await page.getByRole("button", { name: "Add project", exact: true }).click();
  await page.getByLabel("Project name").fill("Manual project");
  await page
    .locator(".expandable-entry")
    .getByRole("button", { name: "Add bullet", exact: true })
    .click();
  await page
    .getByLabel("Description bullet 1", { exact: true })
    .fill("A multiline accomplishment\nwith more detail.");
  await page
    .getByLabel("I reviewed these details and confirm saving this profile.")
    .check();
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(
    page.getByText("Changes saved and reloaded from the server."),
  ).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: /Manual career profile/ }).click();
  await expect(
    page.getByLabel("Description bullet 1", { exact: true }),
  ).toHaveValue("A multiline accomplishment\nwith more detail.");
});
test("Ollama unavailable offers manual entry and retry without erasing a saved profile", async ({
  page,
  request,
}) => {
  test.skip(
    process.env.OLLAMA_TEST_FIXTURE !== "1",
    "Requires controllable synthetic provider",
  );
  await request.post("http://127.0.0.1:11999/test/unavailable");
  try {
    await register(page);
    const source = await readFile(
      "../backend/src/test/resources/cvs/standard.txt",
      "utf8",
    );
    await page.getByLabel("Upload PDF").setInputFiles({
      name: "unavailable.pdf",
      mimeType: "application/pdf",
      buffer: pdf(source),
    });
    await expect(page.getByRole("alert")).toContainText(
      "Ollama is unavailable",
    );
    await page.getByRole("button", { name: "Manual profile entry" }).click();
    await page.getByLabel("Full name", { exact: true }).fill("Manual recovery");
    await page
      .getByLabel("I reviewed these details and confirm saving this profile.")
      .check();
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(
      page.getByText("Changes saved and reloaded from the server."),
    ).toBeVisible();
    await page.getByRole("button", { name: "Retry AI extraction" }).click();
    await expect(page.getByRole("alert")).toContainText(
      "Ollama is unavailable",
    );
    await expect(page.getByLabel("Full name", { exact: true })).toHaveValue(
      "Manual recovery",
    );
  } finally {
    await request.post("http://127.0.0.1:11999/test/available");
  }
});
