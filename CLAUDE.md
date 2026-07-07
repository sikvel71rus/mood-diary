# Mood Diary — Project Constitution for Claude Code

Read `docs/PROJECT_CONTEXT.md` at the start of every session for full context.
Core artifacts: `docs/hypothesis.md` → `docs/research.md` → `docs/constitution.md` → `docs/spec.md` → `docs/plan.md` → `docs/tasks.md`

---

## What we're building
Android app — daily mood diary that detects patterns (primarily sleep↔mood) and gives soft personalized recommendations. Real client, real data from day one.

## Tech stack (approved)
- Backend: **Python** (framework chosen at W3)
- Frontend: **Android native, Kotlin** + Jetpack Compose (confirm with Nikitka before W3)
- DB: **PostgreSQL**
- No paid external APIs

## Hard rules
- Log one entry ≤ 15–20 sec (non-negotiable UX requirement from research)
- First insight shown after 3–5 entries, not weeks
- Sleep→mood correlation as first recommendation rule (scientifically proven, no ML needed)
- Secrets in .env only, never in code
- No third-party analytics SDKs, no logging personal data in plaintext

## Out of scope (MVP)
External tracker integrations, push notifications, full ML, specialist consultations, web version.

## Agent autonomy
**Can do independently:** write/refactor backend and frontend code within agreed stack and MVP scope, add tests, propose minor technical decisions.
**Must ask before:** changing stack (language, DB, framework), going outside MVP scope, any decision affecting user data privacy.

## Current stage
W1 Discovery ✅ → **W2 Specify** (spec.md due 15.07.2026) → W3 Plan (22.07) → W4 Implement (29.07)
