// Explicitly opt-in wire fixture, used only by browser contract tests. This is NOT an AI engine.
import http from "node:http";
if (process.env.OLLAMA_TEST_FIXTURE !== "1")
  throw new Error(
    "Set OLLAMA_TEST_FIXTURE=1 for synthetic contract tests only.",
  );
let unavailable = false;
function empty(schema) {
  if (schema.type === "object")
    return Object.fromEntries(
      Object.entries(schema.properties).map(([key, value]) => [
        key,
        empty(value),
      ]),
    );
  if (schema.type === "array") return [];
  return null;
}
http
  .createServer(async (req, res) => {
    if (req.url === "/health") {
      res.end("test fixture only");
      return;
    }
    if (req.url === "/test/unavailable") {
      unavailable = true;
      res.end("ok");
      return;
    }
    if (req.url === "/test/available") {
      unavailable = false;
      res.end("ok");
      return;
    }
    if (req.url !== "/api/chat" || unavailable) {
      res.writeHead(503);
      res.end("{}");
      return;
    }
    let data = "";
    for await (const chunk of req) data += chunk;
    const request = JSON.parse(data),
      schema = request.format,
      source = request.messages[1].content;
    const profile = empty(schema);
    profile.fullName = "Jordan Lee";
    profile.email = "jordan@example.com";
    profile.phone = "invented unsupported phone";
    const project = empty(schema.properties.projects.items);
    Object.assign(project, {
      name: "Scalable Bookstore Microservices Platform",
      technologies: ["Java", "Spring Boot", "PostgreSQL"],
      description: [
        "Built an inventory service with Java.",
        "Added resilient checkout workflows.",
        "Deployed services with Docker.",
      ],
      originalText: source,
    });
    profile.projects = [project];
    const job = empty(schema.properties.experience.items);
    Object.assign(job, {
      company: "Example Co",
      jobTitle: "Engineer",
      responsibilities: [
        "Built REST APIs.",
        "Reviewed code with the team.",
        "Improved automated tests.",
      ],
      originalText: source,
    });
    profile.experience = [job];
    res.setHeader("Content-Type", "application/json");
    res.end(
      JSON.stringify({
        done: true,
        done_reason: "stop",
        message: { role: "assistant", content: JSON.stringify(profile) },
      }),
    );
  })
  .listen(11999, "127.0.0.1", () =>
    console.log(
      "Synthetic Ollama protocol fixture listening on 11999; no model inference.",
    ),
  );
