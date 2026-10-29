import { test, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
// Minimal valid text PDF, generated locally; no personal data or external fixture.
function pdf() {
  const stream =
    "BT /F1 12 Tf 50 750 Td (Jordan Lee) Tj 0 -20 Td (jordan@example.com) Tj 0 -20 Td (Skills) Tj 0 -20 Td (Java, SQL) Tj ET";
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
test("real auth, PDF upload, nested editing, persistence, history, isolation and mobile", async ({
  page,
  request,
}) => {
  const email = `browser-${Date.now()}@example.com`;
  const password = "Local-test-password-123";
  await mkdir("../docs/screenshots", { recursive: true });
  await page.goto("/");
  await page.screenshot({
    path: "../docs/screenshots/login.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Create an account" }).click();
  await page.getByLabel("Full name").fill("Jordan Lee");
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password").fill(password);
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(page.getByText("A fresh start")).toBeVisible();
  await page.getByLabel("Upload PDF").setInputFiles({
    name: "jordan-cv.pdf",
    mimeType: "application/pdf",
    buffer: pdf(),
  });
  await expect(page.getByLabel("Full name", { exact: true })).toHaveValue(
    "Jordan Lee",
  );
  await page.getByLabel("Full name", { exact: true }).fill("Jordan Updated");
  await page
    .getByLabel("Summary", { exact: true })
    .fill("My browser-tested summary");
  await page
    .getByRole("button", { name: "Add experience", exact: true })
    .click();
  await page.getByLabel("Company", { exact: true }).fill("Example Co");
  await page.getByLabel("Job title", { exact: true }).fill("Engineer");
  await page
    .getByLabel("Responsibilities — one per line")
    .fill("Built APIs\nReviewed code");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  await expect(
    page.getByText("Changes saved and reloaded from the server."),
  ).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: "../docs/screenshots/workspace.png",
    fullPage: false,
  });
  await page.reload();
  await page.getByRole("button", { name: /jordan-cv.pdf/ }).click();
  await expect(page.getByLabel("Full name", { exact: true })).toHaveValue(
    "Jordan Updated",
  );
  await expect(page.getByLabel("Company", { exact: true })).toHaveValue(
    "Example Co",
  );
  await page.getByText("Raw extracted PDF text", { exact: true }).click();
  await expect(page.locator("pre").first()).toContainText("Jordan Lee");
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../docs/screenshots/mobile.png",
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByText("Welcome back.")).toBeVisible();
  await expect(page.getByText("jordan-cv.pdf")).toHaveCount(0);
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("button", { name: /jordan-cv.pdf/ }),
  ).toBeVisible();
  const owner = await request.post("/api/auth/login", {
    data: { email, password },
  });
  expect(owner.ok()).toBeTruthy();
  const ownerData = await owner.json();
  const list = await request.get("/api/resumes", {
    headers: { Authorization: `Bearer ${ownerData.token}` },
  });
  const rows = await list.json();
  expect(rows).toHaveLength(1);
  expect(rows[0].extractedText).toBeUndefined();
  const other = await request.post("/api/auth/register", {
    data: { email: `other-${email}`, password, fullName: "Other User" },
  });
  const otherData = await other.json();
  const headers = { Authorization: `Bearer ${otherData.token}` };
  expect(await (await request.get("/api/resumes", { headers })).json()).toEqual(
    [],
  );
  expect(
    (await request.get(`/api/resumes/${rows[0].id}`, { headers })).status(),
  ).toBe(404);
  expect(
    (
      await request.put(`/api/resumes/${rows[0].id}/profile`, {
        headers,
        data: { fullName: "Intruder" },
      })
    ).status(),
  ).toBe(404);
  expect(
    (
      await request.get("/api/resumes", {
        headers: { Authorization: "Bearer invalid" },
      })
    ).status(),
  ).toBe(401);
});
