import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  ArrowUpRight,
  Check,
  FileText,
  FolderOpen,
  LayoutDashboard,
  LogOut,
  Plus,
  ShieldCheck,
  Sparkles,
  Upload,
  ChevronRight,
  Trash2,
} from "lucide-react";
import {
  ApiError,
  request,
  readSession,
  validSession,
  type Session,
  type Profile,
  type Resume,
  type Summary,
  type Entry,
} from "./api";
const date = (value: string) =>
  new Date(value).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
export default function App() {
  const [session, setSession] = useState<Session | null>(readSession);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [history, setHistory] = useState<Summary[]>([]);
  const [resume, setResume] = useState<Resume | null>(null);
  const [profile, setProfile] = useState<Profile>({});
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dirty, setDirty] = useState(false);
  const [libraryError, setLibraryError] = useState("");
  const generation = useRef(0);
  const fileInput = useRef<HTMLInputElement>(null);
  function logout(message = "") {
    setAuthMode("login");
    generation.current++;
    sessionStorage.removeItem("applypilot-session");
    setSession(null);
    setResume(null);
    setProfile({});
    setHistory([]);
    setDirty(false);
    setBusy("");
    setError(message);
    setNotice("");
    setLibraryError("");
  }
  function failure(e: unknown) {
    if (e instanceof ApiError && e.status === 401) logout(e.message);
    else
      setError(
        e instanceof Error
          ? e.message
          : "Something went wrong. Please try again.",
      );
  }
  useEffect(() => {
    if (!session) return;
    let active = true;
    const token = session.token;
    setBusy("Loading library");
    setLibraryError("");
    request<Summary[]>("/resumes", token)
      .then((rows) => {
        if (active) setHistory(rows);
      })
      .catch((e) => {
        if (active) {
          if (e instanceof ApiError && e.status === 401) logout(e.message);
          else setLibraryError(e.message);
        }
      })
      .finally(() => {
        if (active) setBusy("");
      });
    const timer = window.setInterval(() => {
      if (!validSession(session))
        logout("Your session has expired. Please sign in again.");
    }, 1000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [session]);
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);
  const canLeave = () =>
    !dirty || window.confirm("Discard your unsaved profile changes?");
  async function authenticate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy("Signing in");
    setError("");
    const data = new FormData(e.currentTarget);
    try {
      const result = await request<Session>("/auth/" + authMode, undefined, {
        method: "POST",
        body: JSON.stringify(Object.fromEntries(data)),
      });
      sessionStorage.setItem("applypilot-session", JSON.stringify(result));
      generation.current++;
      setSession(result);
    } catch (e) {
      setError(
        e instanceof ApiError && e.status !== 0
          ? "Unable to sign in or create an account. Check your credentials; registration needs a unique email and a password of at least 8 characters."
          : (e as Error).message,
      );
    } finally {
      setBusy("");
    }
  }
  function install(value: Resume) {
    setResume(value);
    setProfile(
      structuredClone(
        value.parsedProfile || { originalText: value.extractedText },
      ),
    );
    setDirty(false);
  }
  async function select(id: number) {
    if (!canLeave() || !session) return;
    const g = generation.current;
    setBusy("Loading resume");
    setError("");
    setNotice("");
    try {
      const value = await request<Resume>("/resumes/" + id, session.token);
      if (g === generation.current) install(value);
    } catch (e) {
      if (g === generation.current) failure(e);
    } finally {
      if (g === generation.current) setBusy("");
    }
  }
  async function refresh() {
    if (!session) return;
    const g = generation.current;
    setBusy("Loading library");
    setLibraryError("");
    try {
      const rows = await request<Summary[]>("/resumes", session.token);
      if (g === generation.current) setHistory(rows);
    } catch (e) {
      if (g === generation.current) {
        if (e instanceof ApiError && e.status === 401) failure(e);
        else setLibraryError((e as Error).message);
      }
    } finally {
      if (g === generation.current) setBusy("");
    }
  }
  async function upload(file?: File) {
    if (!file || !session) return;
    setError("");
    setNotice("");
    if (
      !file.name.toLowerCase().endsWith(".pdf") ||
      (file.type && file.type !== "application/pdf") ||
      file.size === 0 ||
      file.size > 5 * 1024 * 1024
    ) {
      setError("Choose a non-empty PDF file under 5 MB.");
      return;
    }
    if (!canLeave()) return;
    const g = generation.current;
    setBusy("Uploading and extracting PDF");
    const form = new FormData();
    form.append("file", file, file.name);
    try {
      const value = await request<Resume>("/resumes/upload", session.token, {
        method: "POST",
        body: form,
      });
      if (g !== generation.current) return;
      install(value);
      setHistory((rows) => [
        {
          id: value.id,
          fileName: value.fileName,
          uploadedAt: value.uploadedAt,
          hasProfile: !!value.parsedProfile,
          parsingError: value.parsingError,
        },
        ...rows,
      ]);
      setNotice("PDF saved. Review the extracted details below.");
    } catch (e) {
      if (g === generation.current) failure(e);
    } finally {
      if (g === generation.current) setBusy("");
      if (fileInput.current) fileInput.current.value = "";
    }
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!resume || !session) return;
    const g = generation.current;
    setBusy("Saving and verifying profile");
    setError("");
    setNotice("");
    try {
      await request<Resume>(`/resumes/${resume.id}/profile`, session.token, {
        method: "PUT",
        body: JSON.stringify(profile),
      });
      const saved = await request<Resume>(
        `/resumes/${resume.id}`,
        session.token,
      );
      if (g !== generation.current) return;
      install(saved);
      setHistory((rows) =>
        rows.map((r) =>
          r.id === saved.id
            ? {
                ...r,
                hasProfile: !!saved.parsedProfile,
                parsingError: saved.parsingError,
              }
            : r,
        ),
      );
      setNotice("Changes saved and reloaded from the server.");
    } catch (e) {
      if (g === generation.current) failure(e);
    } finally {
      if (g === generation.current) setBusy("");
    }
  }
  function change(key: keyof Profile, value: unknown) {
    setProfile((p) => ({ ...p, [key]: value }));
    setDirty(true);
    setNotice("");
  }
  if (!session)
    return (
      <div className="auth-layout">
        <section className="auth-story">
          <Brand />
          <div>
            <span className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</span>
            <h1>
              A clearer picture.
              <br />A stronger next step.
            </h1>
            <p>
              Bring your experience together in one place. Upload your CV,
              review every detail, and build a profile that feels like you.
            </p>
            <div className="story-card">
              <FileText size={30} />
              <div>
                <strong>Your experience, organised.</strong>
                <p>
                  Real PDF extraction. Editable profiles. Always yours to
                  review.
                </p>
              </div>
            </div>
          </div>
          <small>ApplyPilot AI · Your career workspace</small>
        </section>
        <main className="auth-main">
          <div className="auth-box">
            <span className="pill">
              <Sparkles size={14} /> LOCAL MVP
            </span>
            <h2>
              {authMode === "login" ? "Welcome back." : "Make your next move."}
            </h2>
            <p>
              {authMode === "login"
                ? "Sign in to your resume workspace."
                : "Create your account to start organising your CVs."}
            </p>
            {error && (
              <div role="alert" className="alert error">
                {error}
              </div>
            )}
            <form onSubmit={authenticate}>
              {authMode === "register" && (
                <label>
                  Full name
                  <input
                    name="fullName"
                    autoComplete="name"
                    required
                    maxLength={200}
                  />
                </label>
              )}
              <label>
                Email address
                <input
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                />
              </label>
              <label>
                Password
                <input
                  name="password"
                  type="password"
                  autoComplete={
                    authMode === "login" ? "current-password" : "new-password"
                  }
                  required
                  minLength={authMode === "register" ? 8 : 1}
                />
              </label>
              <button className="primary wide" disabled={!!busy}>
                {busy || (authMode === "login" ? "Sign in" : "Create account")}
                <ArrowUpRight size={18} />
              </button>
            </form>
            <p className="auth-switch">
              {authMode === "login"
                ? "New to ApplyPilot?"
                : "Already have an account?"}{" "}
              <button
                onClick={() => {
                  setAuthMode(authMode === "login" ? "register" : "login");
                  setError("");
                }}
                disabled={!!busy}
              >
                {authMode === "login" ? "Create an account" : "Sign in"}
              </button>
            </p>
            <div className="privacy">
              <ShieldCheck size={18} />
              Your CV stays in your local backend. No external AI service.
            </div>
          </div>
        </main>
      </div>
    );
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <Brand />
        <div className="workspace-label">WORKSPACE</div>
        <button
          className="nav active"
          onClick={() =>
            document
              .getElementById("library")
              ?.scrollIntoView({ behavior: "smooth" })
          }
        >
          <FolderOpen size={19} />
          Resume workspace<span>{history.length}</span>
        </button>
        {["Job descriptions", "Resume matching", "Applications"].map(
          (label) => (
            <button className="nav planned" disabled key={label}>
              <LayoutDashboard size={18} />
              <span>
                {label}
                <small>Coming soon</small>
              </span>
            </button>
          ),
        )}
        <div className="sidebar-note">
          <Sparkles size={19} />
          <strong>Small steps. Better clarity.</strong>
          <p>
            Start with your CV. Review the details and make your experience
            shine.
          </p>
        </div>
        <div className="account">
          <span className="avatar">
            {session.fullName?.slice(0, 1).toUpperCase() || "U"}
          </span>
          <div>
            <strong>{session.fullName}</strong>
            <small>{session.email}</small>
          </div>
          <button
            aria-label="Log out"
            title="Log out"
            onClick={() => {
              if (canLeave()) logout();
            }}
          >
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="main-wrap">
        <header className="topbar">
          <span>
            Workspace <ChevronRight size={14} /> <strong>My resumes</strong>
          </span>
          <span className="local-badge">
            <i />
            Local workspace
          </span>
        </header>
        <main className="dashboard">
          <div className="page-heading">
            <div>
              <span className="eyebrow">BUILD YOUR NEXT CHAPTER</span>
              <h1>Your experience. All together.</h1>
              <p>
                Upload, review and refine your CV. A little clarity goes a long
                way.
              </p>
            </div>
            <span className="version">MVP / 01</span>
          </div>
          <section className="upload-panel">
            <div className="upload-icon">
              <Upload size={28} />
            </div>
            <div>
              <h2>Let’s start with your CV</h2>
              <p>
                Upload a PDF and turn your experience into an editable profile.
              </p>
              <small>PDF only · Up to 5 MB · Text-based PDFs work best</small>
            </div>
            <label
              className={"primary upload-button " + (busy ? "disabled" : "")}
            >
              {" "}
              <Plus size={18} />
              Upload a CV
              <input
                ref={fileInput}
                aria-label="Upload PDF"
                type="file"
                accept=".pdf,application/pdf"
                disabled={!!busy}
                onChange={(e) => void upload(e.target.files?.[0])}
              />
            </label>
          </section>
          {busy && (
            <div className="progress" role="status">
              <span className="spinner" />
              {busy}…
            </div>
          )}
          {error && (
            <div role="alert" className="alert error">
              {error}
            </div>
          )}
          {notice && (
            <div role="status" className="alert success">
              <Check size={18} />
              {notice}
            </div>
          )}
          <div className="workspace-grid">
            <section className="library" id="library">
              <div className="section-heading">
                <h2>
                  Resume library <span>{history.length}</span>
                </h2>
                <button
                  className="text-button"
                  disabled={!!busy}
                  onClick={() => void refresh()}
                >
                  Refresh
                </button>
              </div>
              <p className="subtle">
                Saved securely to your account · Newest first
              </p>
              {libraryError && (
                <div className="alert error" role="alert">
                  {libraryError}
                  <button onClick={() => void refresh()}>Try again</button>
                </div>
              )}
              <div className="resume-list">
                {history.map((item) => (
                  <button
                    key={item.id}
                    className={
                      "resume-card " +
                      (resume?.id === item.id ? "selected" : "")
                    }
                    onClick={() => void select(item.id)}
                    disabled={!!busy}
                  >
                    <span className="pdf-icon">
                      <FileText size={22} />
                      <small>PDF</small>
                    </span>
                    <span className="resume-meta">
                      <strong>{item.fileName}</strong>
                      <small>{date(item.uploadedAt)}</small>
                      <span
                        className={
                          "state " + (item.parsingError ? "warning" : "")
                        }
                      >
                        {item.parsingError
                          ? "Needs review"
                          : item.hasProfile
                            ? "Profile available"
                            : "Ready for manual entry"}
                      </span>
                    </span>
                    <ChevronRight size={16} />
                  </button>
                ))}
                {!history.length && !busy && !libraryError && (
                  <div className="empty-library">
                    <FolderOpen size={30} />
                    <h3>A fresh start</h3>
                    <p>
                      Your uploaded CVs will appear here, ready whenever you
                      sign in.
                    </p>
                  </div>
                )}
              </div>
              <div className="tip">
                <ShieldCheck size={18} />
                <p>Only you can access the CVs in your library.</p>
              </div>
            </section>
            <section className="editor-panel" aria-label="Profile editor">
              {resume ? (
                <>
                  <div className="editor-heading">
                    <div>
                      <span className="eyebrow">REVIEW & REFINE</span>
                      <h2>{resume.fileName}</h2>
                      <p>Uploaded {date(resume.uploadedAt)}</p>
                    </div>
                    <span className="pill">
                      {dirty ? "Unsaved changes" : "Saved profile"}
                    </span>
                  </div>
                  {resume.parsingError ? (
                    <div className="alert warning" role="alert">
                      {resume.parsingError} You can enter a profile manually;
                      the source text is preserved.
                    </div>
                  ) : !resume.parsedProfile ? (
                    <div className="alert warning">
                      No structured profile is available. Enter details manually
                      using the PDF text below.
                    </div>
                  ) : (
                    <div className="review-note">
                      <Check size={16} />
                      Extraction available. Review for accuracy before saving.
                    </div>
                  )}
                  <form onSubmit={save}>
                    <fieldset disabled={!!busy}>
                      <div className="form-section">
                        <h3>
                          01 <span>Contact details</span>
                        </h3>
                        <p>
                          Editable profile fields. Corrections are saved to this
                          CV.
                        </p>
                        <div className="field-grid">
                          {(
                            ["fullName", "email", "phone", "location"] as const
                          ).map((key) => (
                            <label key={key}>
                              {
                                {
                                  fullName: "Full name",
                                  email: "Email",
                                  phone: "Phone",
                                  location: "Location",
                                }[key]
                              }
                              <input
                                type={key === "email" ? "email" : "text"}
                                maxLength={1000}
                                value={profile[key] ?? ""}
                                onChange={(e) => change(key, e.target.value)}
                              />
                            </label>
                          ))}
                        </div>
                      </div>
                      <div className="form-section">
                        <h3>
                          02 <span>Professional summary</span>
                        </h3>
                        <label>
                          Summary
                          <textarea
                            rows={4}
                            maxLength={200000}
                            value={profile.summary ?? ""}
                            onChange={(e) => change("summary", e.target.value)}
                          />
                        </label>
                      </div>
                      <div className="form-section">
                        <h3>
                          03 <span>Skills</span>
                        </h3>
                        <ListField
                          label="Skills — one per line"
                          value={profile.skills}
                          onChange={(v) => change("skills", v)}
                        />
                      </div>
                      {(["education", "experience", "projects"] as const).map(
                        (key, i) => (
                          <EntrySection
                            key={key}
                            name={key}
                            number={i + 4}
                            entries={profile[key]}
                            onChange={(v) => change(key, v)}
                          />
                        ),
                      )}
                      <div className="source-section">
                        <h3>Source & extraction notes</h3>
                        <p>
                          Read-only reference. Original and unmatched text are
                          preserved when you save.
                        </p>
                        <details>
                          <summary>Raw extracted PDF text</summary>
                          <pre>
                            {resume.extractedText ||
                              "No text was extracted. Scanned PDFs need OCR, which is not supported yet."}
                          </pre>
                        </details>
                        <details>
                          <summary>Unmatched text / possible omissions</summary>
                          <pre>
                            {profile.unmatchedText ||
                              "No unmatched text recorded."}
                          </pre>
                        </details>
                        <details>
                          <summary>Profile original text</summary>
                          <pre>
                            {profile.originalText ||
                              "No profile original text recorded."}
                          </pre>
                        </details>
                      </div>
                      <div className="save-bar">
                        <span>
                          {dirty
                            ? "You have unsaved changes"
                            : "All changes saved"}
                          <small>
                            Saving verifies persistence with a fresh server
                            read.
                          </small>
                        </span>
                        <button
                          type="button"
                          className="secondary"
                          disabled={!dirty}
                          onClick={() => {
                            if (canLeave()) install(resume);
                          }}
                        >
                          Discard
                        </button>
                        <button className="primary" disabled={!dirty}>
                          <Check size={17} />
                          Save changes
                        </button>
                      </div>
                    </fieldset>
                  </form>
                </>
              ) : (
                <div className="empty-editor">
                  <div className="empty-art">
                    <FileText size={54} />
                    <span>
                      <Check size={20} />
                    </span>
                  </div>
                  <span className="eyebrow">
                    A LITTLE REVIEW. A LOT OF CONFIDENCE.
                  </span>
                  <h2>Your story, ready for its next step.</h2>
                  <p>
                    Select a CV from your library, or upload your first one.
                    <br />
                    We’ll help you turn the details into a clearer profile.
                  </p>
                  <div className="steps">
                    <span>01 Upload your PDF</span>
                    <span>02 Review your details</span>
                    <span>03 Save your profile</span>
                  </div>
                </div>
              )}
            </section>
          </div>
          <footer>
            ApplyPilot AI <span>Built for your next chapter.</span>
            <span>Rule-based extraction · Review always recommended</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
function Brand() {
  return (
    <div className="brand">
      <span>
        <ArrowUpRight size={24} />
      </span>
      ApplyPilot<span className="brand-ai">AI</span>
    </div>
  );
}
function ListField({
  label,
  value,
  onChange,
}: {
  label: string;
  value?: string[] | null;
  onChange: (v: string[]) => void;
}) {
  return (
    <label>
      {label}
      <textarea
        rows={3}
        value={(value ?? []).join("\n")}
        onChange={(e) =>
          onChange(e.target.value === "" ? [] : e.target.value.split("\n"))
        }
      />
    </label>
  );
}
const schemas = {
  education: [
    "institution",
    "degree",
    "fieldOfStudy",
    "startDate",
    "endDate",
    "location",
  ],
  experience: [
    "company",
    "jobTitle",
    "jobTittle",
    "startDate",
    "endDate",
    "location",
    "responsibilities",
  ],
  projects: ["name", "technologies", "description"],
};
const labels: Record<string, string> = {
  institution: "Institution",
  degree: "Degree",
  fieldOfStudy: "Field of study",
  startDate: "Start date",
  endDate: "End date",
  location: "Location",
  company: "Company",
  jobTitle: "Job title",
  jobTittle: "Legacy job title",
  responsibilities: "Responsibilities",
  name: "Project name",
  technologies: "Technologies",
  description: "Description",
};
function EntrySection({
  name,
  number,
  entries,
  onChange,
}: {
  name: keyof typeof schemas;
  number: number;
  entries?: Entry[] | null;
  onChange: (v: Entry[]) => void;
}) {
  const rows = entries ?? [];
  function update(i: number, key: string, value: string | string[]) {
    onChange(
      rows.map((entry, index) =>
        index === i ? { ...entry, [key]: value } : entry,
      ),
    );
  }
  return (
    <div className="form-section">
      <div className="section-heading">
        <h3>
          0{number}{" "}
          <span>
            {name === "experience"
              ? "Work experience"
              : name[0].toUpperCase() + name.slice(1)}
          </span>
        </h3>
        <button
          type="button"
          className="text-button"
          onClick={() => onChange([...rows, {}])}
        >
          <Plus size={15} />
          Add{" "}
          {name === "education"
            ? "education"
            : name === "experience"
              ? "experience"
              : "project"}
        </button>
      </div>
      {!rows.length && (
        <p>No entries yet. Add any details the parser missed.</p>
      )}
      {rows.map((entry, i) => (
        <div className="entry" key={i}>
          <div className="section-heading">
            <strong>Entry {i + 1}</strong>
            <button
              type="button"
              className="remove"
              aria-label={`Remove ${name} ${i + 1}`}
              onClick={() => onChange(rows.filter((_, index) => index !== i))}
            >
              <Trash2 size={15} />
              Remove
            </button>
          </div>
          <div className="field-grid">
            {schemas[name]
              .filter((key) => key !== "jobTittle" || entry[key] != null)
              .map((key) =>
                ["responsibilities", "technologies", "description"].includes(
                  key,
                ) ? (
                  <ListField
                    key={key}
                    label={`${labels[key]} — one per line`}
                    value={entry[key] as string[] | null}
                    onChange={(v) => update(i, key, v)}
                  />
                ) : (
                  <label key={key}>
                    {labels[key]}
                    <input
                      maxLength={1000}
                      value={(entry[key] as string) ?? ""}
                      onChange={(e) => update(i, key, e.target.value)}
                    />
                  </label>
                ),
              )}
          </div>
          {entry.originalText && (
            <details>
              <summary>Original entry text</summary>
              <pre>{entry.originalText}</pre>
            </details>
          )}
        </div>
      ))}
    </div>
  );
}
