---
name: create-pr
description: "Draft and create a GitHub PR for the current branch: reads git history and diff, links the HISS Plane ticket(s) it closes, and opens the PR via gh CLI. Use whenever asked to create or open a pull request, as the last step after implement-ticket/verify-ticket/review."
allowed-tools: Read, Bash, Glob, Grep, mcp__plane__retrieve_work_item
---

# create-pr — Draft and Create a Pull Request

## Purpose

Produce a PR description from the current branch's commits/diff and the HISS
ticket(s) it implements, then open it via `gh pr create`.

Only meaningful for the branch workflow (`implement-ticket`'s default). If the
ticket was committed straight to `main` (the small-ticket exception), there's no
PR to open — tell the user that and stop.

---

## Step 1 — Gather branch context

Run in parallel:

```bash
git log main...HEAD --oneline
git diff main...HEAD --stat
git diff main...HEAD --name-only --diff-filter=d
git diff main...HEAD
```

From the branch name (`hiss-<nnn>-<slug>`) and/or commit bodies' `Implements
HISS-NNN.` lines, extract every HISS ticket id this branch touches — usually
one, occasionally more if small adjacent tickets were done together.

---

## Step 2 — Check for a PR template

```
Glob: .github/pull_request_template.md
```

If it exists, read it and fill in its sections exactly — don't invent sections
it doesn't have. If it doesn't exist (true as of this writing — this repo has no
`.github/` directory), use the default structure in Step 4.

---

## Step 3 — Pull ticket context

For each HISS ticket id found in Step 1, look it up in
`.claude/skills/_shared/plane-project.md` and fetch it:

```
mcp__plane__retrieve_work_item
  project_id: 69616f0e-7541-46e7-94dd-3a261ab0bd05
  work_item_id: <from the table>
```

Pull its title and phase module — these feed the Summary. Don't dump the
ticket's full description into the PR; the reader can open the ticket. **Don't
fabricate a Plane URL** — link it by human id only (`HISS-NNN`), unless a real
workspace URL was already established earlier in this conversation.

If the diff touches any `*Screen.kt`/`*Previews.kt` and `screenshots/` has
changes (`git diff main...HEAD --name-only -- screenshots/`), note the PNG
paths — they belong in the Demo section.

---

## Step 4 — Draft the PR title and body

### Title

Concise, present-tense, under 70 characters. If one ticket: lead with its
title (e.g. "Add clinical domain models" for HISS-101). If several small
tickets: a summary covering all of them.

### Body (default structure, no template present)

```markdown
## Summary

- <one bullet per logical change group, not per commit — lead with what,
  include why where non-obvious>

## Demo

<Screenshot paths under screenshots/ if any changed, as relative links.
If the change is visual but no screenshots exist yet, say so and suggest
running `match-mock-fidelity` / recording Roborazzi goldens before merging.
If non-visual (models, repositories, tests only): "N/A — no UI changes.">

## References

- Implements HISS-NNN <title> (<phase module>)
- ...one line per ticket from Step 1

## Test plan

- [ ] `./gradlew :composeApp:compileKotlinDesktop` — clean
- [ ] `./gradlew :composeApp:detekt` — clean
- [ ] `./gradlew :composeApp:desktopTest` — clean
- [ ] <one checkbox per new/changed test file, naming it>
- [ ] <any manual verification step carried over from verify-ticket's report>
```

Be specific in the Test plan — "confirm the patient list shows all 6 seeded
patients" beats "test the feature."

---

## Step 5 — Present the draft for approval

Show the complete draft title and body, and ask:

> "Does this look good, or would you like to change anything before I create
> the PR?"

Do **not** call `gh pr create` until the user explicitly approves.

---

## Step 6 — Create the PR

```bash
git push -u origin HEAD
gh pr create --title "<title>" --body "$(cat <<'EOF'
<body>
EOF
)"
```

Return the PR URL. This is the last step of the local pipeline — from here the
PR needs a human review/merge (this repo has no CI configured as of this
writing). Remind the user: once it merges, move the ticket(s) to **Done** in
Plane — no skill does that automatically (see `_shared/plane-project.md`'s
pipeline note on why).
