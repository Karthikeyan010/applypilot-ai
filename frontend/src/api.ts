export type Entry = Record<string, string | string[] | null>;
export type Profile = {
  fullName?: string | null;
  email?: string | null;
  phone?: string | null;
  location?: string | null;
  summary?: string | null;
  originalText?: string | null;
  unmatchedText?: string | null;
  skills?: string[] | null;
  education?: Entry[] | null;
  experience?: Entry[] | null;
  projects?: Entry[] | null;
};
export type ExtractionDraft = {
  profile: Profile;
  warnings: { field: string; message: string }[];
  provider: string;
  model: string;
};
export type Resume = {
  profileConfirmedAt?: string | null;
  extractionDraft?: ExtractionDraft | null;
  id: number;
  fileName: string;
  uploadedAt: string;
  extractedText: string;
  parsedProfile: Profile | null;
  parsingError: string | null;
};
export type Summary = Pick<
  Resume,
  "id" | "fileName" | "uploadedAt" | "parsingError"
> & { hasProfile: boolean; hasDraft?: boolean };
export type Session = {
  token: string;
  fullName: string;
  email: string;
  userId: number;
};
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function request<T>(
  path: string,
  token?: string,
  options: RequestInit = {},
): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api" + path, {
      ...options,
      headers: {
        ...(options.body instanceof FormData
          ? {}
          : { "Content-Type": "application/json" }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new ApiError(
      0,
      "Cannot reach the server. Check that the backend is running and try again.",
    );
  }
  if (!response.ok) {
    const message =
      response.status === 401
        ? "Your session has expired. Please sign in again."
        : response.status === 413
          ? "This PDF is too large. Choose a file under 5 MB."
          : response.status === 400
            ? "Check your details. Use a readable PDF, a valid email, and keep entries within the field limits."
            : response.status === 404
              ? "This resume is no longer available. Refresh your library."
              : "The server could not complete this request. Please try again.";
    throw new ApiError(response.status, message);
  }
  return response.json() as Promise<T>;
}
export function validSession(session: Session): boolean {
  try {
    const payload = JSON.parse(
      atob(session.token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")),
    );
    return typeof payload.exp === "number" && payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}
export function readSession(): Session | null {
  try {
    const s = JSON.parse(
      sessionStorage.getItem("applypilot-session") || "null",
    );
    return s && validSession(s) ? s : null;
  } catch {
    return null;
  }
}
