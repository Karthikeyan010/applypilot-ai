import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, it, expect, vi } from "vitest";
import App from "./App";
const session = {
  token: `e30.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.sig`,
  fullName: "Jordan Lee",
  email: "jordan@example.com",
  userId: 1,
};
const profile = {
  fullName: "Jordan Lee",
  email: "jordan@example.com",
  summary: "Original summary",
  skills: ["Java", "SQL"],
  originalText: "exact source\r\n",
  unmatchedText: "Awards",
  education: [{ institution: "College", originalText: "college source" }],
  experience: [
    {
      jobTitle: "Engineer",
      jobTittle: "Legacy title",
      responsibilities: ["One", "Two"],
      originalText: "role source",
    },
  ],
  projects: [
    {
      name: "App",
      technologies: ["Java"],
      description: ["Built app"],
      originalText: "project source",
    },
  ],
};
const resume = {
  id: 7,
  fileName: "cv.pdf",
  uploadedAt: "2026-10-20T12:00:00",
  extractedText: "PDF source",
  parsedProfile: profile,
  parsingError: null,
};
const summary = {
  id: 7,
  fileName: "cv.pdf",
  uploadedAt: resume.uploadedAt,
  hasProfile: true,
  parsingError: null,
};
function response(data: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(data), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}
const fetchMock = vi.fn();
beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
  window.confirm = vi.fn(() => true);
});
function signIn() {
  sessionStorage.setItem("applypilot-session", JSON.stringify(session));
}
describe("connected workspace", () => {
  it("registers, protects data, logs out, then restores history after login", async () => {
    const user = userEvent.setup();
    fetchMock.mockImplementation((url: string) =>
      url === "/api/resumes" ? response([summary]) : response(session),
    );
    render(<App />);
    await user.click(screen.getByRole("button", { name: "Create an account" }));
    await user.type(screen.getByLabelText("Full name"), "Jordan Lee");
    await user.type(
      screen.getByLabelText("Email address"),
      "jordan@example.com",
    );
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByRole("button", { name: /cv.pdf/ })).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/register",
      expect.objectContaining({ method: "POST" }),
    );
    await user.click(screen.getByRole("button", { name: "Log out" }));
    expect(screen.queryByText("cv.pdf")).not.toBeInTheDocument();
    expect(sessionStorage.getItem("applypilot-session")).toBeNull();
    await user.type(
      screen.getByLabelText("Email address"),
      "jordan@example.com",
    );
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: /^Sign in$/ }));
    expect(await screen.findByRole("button", { name: /cv.pdf/ })).toBeVisible();
  });
  it("loads, edits and saves a full profile then reloads the server state", async () => {
    signIn();
    let persisted = structuredClone(resume);
    fetchMock.mockImplementation((url: string, options: RequestInit) => {
      if (url === "/api/resumes") return response([summary]);
      if (options?.method === "PUT") {
        persisted = {
          ...resume,
          parsedProfile: JSON.parse(options.body as string),
        };
        return response(persisted);
      }
      return response(persisted);
    });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: /cv.pdf/ }));
    await user.clear(await screen.findByLabelText("Full name"));
    await user.type(screen.getByLabelText("Full name"), "Updated Name");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(
      await screen.findByText("Changes saved and reloaded from the server."),
    ).toBeVisible();
    expect(persisted.parsedProfile).toEqual({
      ...profile,
      fullName: "Updated Name",
    });
    expect(
      fetchMock.mock.calls.filter((c) => c[0] === "/api/resumes/7"),
    ).toHaveLength(2);
    expect(screen.getByLabelText("Full name")).toHaveValue("Updated Name");
  });
  it("uploads a real multipart request and shows parsing failure honestly", async () => {
    signIn();
    fetchMock.mockImplementation((url: string) =>
      url === "/api/resumes"
        ? response([])
        : response({
            ...resume,
            parsedProfile: null,
            parsingError:
              "Resume saved, but structured profile extraction failed.",
          }),
    );
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("A fresh start");
    await user.upload(
      screen.getByLabelText("Upload PDF"),
      new File(["%PDF-1.4"], "new.pdf", { type: "application/pdf" }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "structured profile extraction failed",
    );
    const call = fetchMock.mock.calls.find(
      (c) => c[0] === "/api/resumes/upload",
    )!;
    expect(call[1].body).toBeInstanceOf(FormData);
    expect((call[1].body as FormData).get("file")).toBeInstanceOf(File);
    expect(screen.queryByText(/Extraction available/)).not.toBeInTheDocument();
  });
  it("rejects invalid files, handles network errors and can retry history", async () => {
    signIn();
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    render(<App />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cannot reach the server",
    );
    fetchMock.mockImplementation(() => response([]));
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("A fresh start")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Upload PDF"), {
      target: { files: [new File(["bad"], "bad.txt", { type: "text/plain" })] },
    });
    expect(await screen.findByRole("alert")).toHaveTextContent("non-empty PDF");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
  it("clears protected data on a 401 and never renders a late response after logout", async () => {
    signIn();
    fetchMock.mockImplementation((url: string) =>
      response(url === "/api/resumes" ? [summary] : resume),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: /cv.pdf/ }));
    fetchMock.mockImplementation(() => response({}, 401));
    await waitFor(() =>
      expect(screen.getByLabelText("Full name")).toBeVisible(),
    );
    await user.click(screen.getByRole("button", { name: "Refresh" }));
    expect(await screen.findByText("Welcome back.")).toBeVisible();
    expect(screen.queryByText("cv.pdf")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("session has expired");
  });
  it("keeps edits after failed saves and supports adding nested entries", async () => {
    signIn();
    fetchMock.mockImplementation((url: string, options: RequestInit) =>
      options?.method === "PUT"
        ? response({}, 400)
        : response(url === "/api/resumes" ? [summary] : resume),
    );
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: /cv.pdf/ }));
    await user.click(
      await screen.findByRole("button", { name: "Add project" }),
    );
    const fields = screen.getAllByLabelText("Project name");
    await user.type(fields[1], "New project");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Check your details",
    );
    expect(fields[1]).toHaveValue("New project");
    expect(screen.getByText("Unsaved changes")).toBeVisible();
  });
});

it("ignores an in-flight resume response after logout", async () => {
  signIn();
  let resolveResume!: (response: Response) => void;
  fetchMock.mockImplementation((url: string) =>
    url === "/api/resumes"
      ? response([summary])
      : new Promise<Response>((resolve) => {
          resolveResume = resolve;
        }),
  );
  const user = userEvent.setup();
  render(<App />);
  await user.click(await screen.findByRole("button", { name: /cv.pdf/ }));
  await user.click(screen.getByRole("button", { name: "Log out" }));
  resolveResume(new Response(JSON.stringify(resume), { status: 200 }));
  await waitFor(() => expect(screen.getByText("Welcome back.")).toBeVisible());
  expect(screen.queryByDisplayValue("Jordan Lee")).not.toBeInTheDocument();
  expect(screen.queryByText("cv.pdf")).not.toBeInTheDocument();
});

it("does not show a workspace for an expired stored session", () => {
  sessionStorage.setItem(
    "applypilot-session",
    JSON.stringify({
      ...session,
      token: `e30.${btoa(JSON.stringify({ exp: 1 }))}.sig`,
    }),
  );
  render(<App />);
  expect(screen.getByText("Welcome back.")).toBeVisible();
  expect(fetchMock).not.toHaveBeenCalled();
});

it("keeps login failures safe and actionable", async () => {
  fetchMock.mockImplementation(() =>
    response({ token: "sensitive-server-detail" }, 500),
  );
  const user = userEvent.setup();
  render(<App />);
  await user.type(screen.getByLabelText("Email address"), "bad@example.com");
  await user.type(screen.getByLabelText("Password"), "wrong-password");
  await user.click(screen.getByRole("button", { name: /^Sign in$/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Check your credentials",
  );
  expect(screen.queryByText("sensitive-server-detail")).not.toBeInTheDocument();
});
