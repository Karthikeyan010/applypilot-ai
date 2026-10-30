import { Plus, Trash2, ArrowUp, ArrowDown } from "lucide-react";
import type { Entry } from "./api";

type Section = "education" | "experience" | "projects";
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
  responsibilities: "Responsibility",
  name: "Project name",
  technologies: "Technology",
  description: "Description bullet",
};

/** Explicit user merge: keep all distinct scalar values, all list items and all source passages. */
export function mergeEntries(first: Entry, second: Entry): Entry {
  const result = { ...first };
  for (const [key, value] of Object.entries(second)) {
    const current = result[key];
    if (value == null) {
      if (!(key in result)) result[key] = value;
      continue;
    }
    if (Array.isArray(value))
      result[key] = [...(Array.isArray(current) ? current : []), ...value];
    else if (typeof current === "string" && current !== value && current)
      result[key] =
        `${current}${key === "originalText" ? "\n\n" : " / "}${value}`;
    else result[key] = value;
  }
  return result;
}
function move<T>(items: T[], from: number, to: number): T[] {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(to, 0, item);
  return result;
}
export function BulletEditor({
  label,
  items,
  onChange,
  onSplit,
}: {
  label: string;
  items: string[] | null | undefined;
  onChange: (v: string[]) => void;
  onSplit?: (index: number) => void;
}) {
  const values = items ?? [];
  return (
    <div className="bullet-editor">
      <strong>
        {label === "Responsibility"
          ? "Responsibilities"
          : label === "Technology"
            ? "Technologies"
            : `${label}s`}
      </strong>
      {values.map((value, i) => (
        <div className="bullet-row" key={i}>
          <label>
            {label} {i + 1}
            <textarea
              aria-label={`${label} ${i + 1}`}
              rows={4}
              maxLength={10000}
              value={value}
              onChange={(e) =>
                onChange(values.map((v, n) => (n === i ? e.target.value : v)))
              }
            />
          </label>
          <div className="entry-actions">
            <button
              type="button"
              aria-label={`Move ${label.toLowerCase()} ${i + 1} up`}
              disabled={i === 0}
              onClick={() => onChange(move(values, i, i - 1))}
            >
              <ArrowUp size={14} />
              Up
            </button>
            <button
              type="button"
              aria-label={`Move ${label.toLowerCase()} ${i + 1} down`}
              disabled={i === values.length - 1}
              onClick={() => onChange(move(values, i, i + 1))}
            >
              <ArrowDown size={14} />
              Down
            </button>
            <button
              type="button"
              aria-label={`Remove ${label.toLowerCase()} ${i + 1}`}
              onClick={() => onChange(values.filter((_, n) => n !== i))}
            >
              <Trash2 size={14} />
              {label === "Technology" ? "Remove technology" : "Remove bullet"}
            </button>
            {onSplit && i > 0 && (
              <button type="button" onClick={() => onSplit(i)}>
                Split from this bullet
              </button>
            )}
          </div>
        </div>
      ))}
      <button
        type="button"
        className="text-button"
        onClick={() => onChange([...values, ""])}
      >
        <Plus size={14} />
        {label === "Technology" ? "Add technology" : "Add bullet"}
      </button>
    </div>
  );
}
export function EntrySection({
  name,
  number,
  entries,
  onChange,
}: {
  name: Section;
  number: number;
  entries?: Entry[] | null;
  onChange: (v: Entry[]) => void;
}) {
  const rows = entries ?? [];
  function update(i: number, key: string, value: string | string[]) {
    onChange(
      rows.map((entry, n) => (n === i ? { ...entry, [key]: value } : entry)),
    );
  }
  function split(i: number, key: string, index: number) {
    const entry = rows[i];
    const bullets = entry[key] as string[];
    const second = { ...structuredClone(entry), [key]: bullets.slice(index) };
    onChange([
      ...rows.slice(0, i),
      { ...entry, [key]: bullets.slice(0, index) },
      second,
      ...rows.slice(i + 1),
    ]);
  }
  return (
    <section className="form-section">
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
      {!rows.length && <p>No entries yet. Add details manually.</p>}
      {rows.map((entry, i) => (
        <details className="entry expandable-entry" key={i} open>
          <summary>
            {name === "projects"
              ? entry.name || `Project ${i + 1}`
              : name === "experience"
                ? entry.company || `Experience ${i + 1}`
                : entry.institution || `Education ${i + 1}`}{" "}
            <small>Entry {i + 1} · Click to collapse</small>
          </summary>
          <div className="entry-body">
            <div className="entry-actions">
              <button
                type="button"
                aria-label={`Move ${name} ${i + 1} up`}
                disabled={i === 0}
                onClick={() => onChange(move(rows, i, i - 1))}
              >
                <ArrowUp size={14} />
                Move up
              </button>
              <button
                type="button"
                aria-label={`Move ${name} ${i + 1} down`}
                disabled={i === rows.length - 1}
                onClick={() => onChange(move(rows, i, i + 1))}
              >
                <ArrowDown size={14} />
                Move down
              </button>
              <button
                type="button"
                aria-label={`Remove ${name} ${i + 1}`}
                onClick={() => onChange(rows.filter((_, n) => n !== i))}
              >
                <Trash2 size={14} />
                Remove entry
              </button>
              {i > 0 && (
                <button
                  type="button"
                  aria-label={`Merge ${name} ${i + 1} with previous`}
                  onClick={() =>
                    onChange([
                      ...rows.slice(0, i - 1),
                      mergeEntries(rows[i - 1], entry),
                      ...rows.slice(i + 1),
                    ])
                  }
                >
                  Merge with previous
                </button>
              )}
            </div>
            <div className="field-grid">
              {schemas[name]
                .filter(
                  (key) =>
                    ![
                      "responsibilities",
                      "technologies",
                      "description",
                    ].includes(key) &&
                    (key !== "jobTittle" || entry[key] != null),
                )
                .map((key) => (
                  <label key={key}>
                    {labels[key]}
                    <input
                      maxLength={1000}
                      value={(entry[key] as string) ?? ""}
                      onChange={(e) => update(i, key, e.target.value)}
                    />
                  </label>
                ))}
            </div>
            {schemas[name]
              .filter((key) =>
                ["responsibilities", "technologies", "description"].includes(
                  key,
                ),
              )
              .map((key) => (
                <BulletEditor
                  key={key}
                  label={labels[key]}
                  items={entry[key] as string[] | null}
                  onChange={(v) => update(i, key, v)}
                  onSplit={
                    key === "technologies"
                      ? undefined
                      : (index) => split(i, key, index)
                  }
                />
              ))}
            {entry.originalText && (
              <details>
                <summary>
                  Original entry text / preserved merged passages
                </summary>
                <pre>{entry.originalText}</pre>
              </details>
            )}
          </div>
        </details>
      ))}
    </section>
  );
}
