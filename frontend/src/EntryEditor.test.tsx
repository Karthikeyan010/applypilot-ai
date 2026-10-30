import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { it, expect } from "vitest";
import { EntrySection, mergeEntries } from "./EntryEditor";
import type { Entry } from "./api";
it("edits multiline bullets as one item, merges projects, splits and reorders without losing metadata", async () => {
  const initial: Entry[] = [
    {
      name: "Bookstore",
      technologies: ["Java"],
      description: ["First\nwrapped line", "Second"],
      originalText: "source A",
    },
    {
      name: "Bookstore",
      technologies: ["SQL"],
      description: ["Third"],
      originalText: "source B",
    },
  ];
  let saved: Entry[] = [];
  function Harness() {
    const [rows, setRows] = useState(initial);
    saved = rows;
    return (
      <EntrySection
        name="projects"
        number={6}
        entries={rows}
        onChange={setRows}
      />
    );
  }
  const user = userEvent.setup();
  render(<Harness />);
  expect(screen.getAllByLabelText("Description bullet 1")[0]).toHaveValue(
    "First\nwrapped line",
  );
  await user.click(
    screen.getByRole("button", { name: "Merge projects 2 with previous" }),
  );
  expect(saved).toHaveLength(1);
  expect(saved[0].description).toEqual([
    "First\nwrapped line",
    "Second",
    "Third",
  ]);
  expect(saved[0].technologies).toEqual(["Java", "SQL"]);
  expect(saved[0].originalText).toBe("source A\n\nsource B");
  await user.type(screen.getByLabelText("Description bullet 3"), "\ncontinued");
  expect(saved[0].description).toHaveLength(3);
  await user.click(
    screen.getByRole("button", { name: "Move description bullet 3 up" }),
  );
  expect((saved[0].description as string[])[1]).toBe("Third\ncontinued");
  await user.click(
    screen.getAllByRole("button", { name: "Split from this bullet" })[0],
  );
  expect(saved).toHaveLength(2);
  expect(saved[1].description).toEqual(["Third\ncontinued", "Second"]);
  await user.click(screen.getByRole("button", { name: "Move projects 2 up" }));
  expect(saved[0].description).toEqual(["Third\ncontinued", "Second"]);
});
it("preserves conflicting scalar values and legacy/source data in an explicit merge", () => {
  const value = mergeEntries(
    {
      jobTitle: "Engineer",
      jobTittle: "Old title",
      originalText: "A",
      responsibilities: ["One"],
    },
    {
      jobTitle: "Lead",
      jobTittle: null,
      originalText: "B",
      responsibilities: ["Two"],
    },
  );
  expect(value).toEqual({
    jobTitle: "Engineer / Lead",
    jobTittle: "Old title",
    originalText: "A\n\nB",
    responsibilities: ["One", "Two"],
  });
});
