import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  timeout: 180000,
  workers: 1,
  use: {
    baseURL: process.env.FRONTEND_TEST_URL || "http://127.0.0.1:5173",
    viewport: { width: 1440, height: 1000 },
    screenshot: "only-on-failure",
  },
  reporter: "list",
  webServer:
    process.env.OLLAMA_TEST_FIXTURE === "1"
      ? {
          command: "node e2e/ollama-fixture-server.mjs",
          url: "http://127.0.0.1:11999/health",
          reuseExistingServer: false,
        }
      : undefined,
});
