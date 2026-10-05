# Prompt 3 - Kontrolpanel page

Build the "Kontrolpanel" (admin dashboard) page for the Standflow frontend.

## Reference mockup

Reference mockup: Local screenshot of the Kontrolpanel Figma frame

Look at this image first and match its layout, colors, spacing, typography, and content as closely as you can. Treat it as the visual source of truth for this page.

## Context

Read ARCHITECTURE.md and CODESTANDARD.md in the repo root before writing any code, and follow both. This is page 3 of 3 in the frontend prototype (landing page and stadeholder application form are the other two). This page is only for admins managing julemarked stand applications.

## Scope

Frontend only (frontend/), do not touch backend/. Add React Router if it is not already set up, and register this page at a sensible route (e.g. /kontrolpanel or /admin). Use CSS Modules for styling.

## Layout

Follow the mockup image exactly for structure, this includes the left sidebar navigation, the top bar, the row of KPI stat tiles, the search/filter/action row, and the applications table with colored status badges. Match the exact labels and text shown in the mockup rather than paraphrasing them.

## Data

No backend endpoint exists for this yet. Use local mock/sample data (an array of plain objects) matching what's shown in the mockup, with a short comment marking it as a placeholder to swap for a real API call later. Do not build backend endpoints as part of this task, that is out of scope per ARCHITECTURE.md's frontend/backend boundary.

## Acceptance criteria

- [ ] Page renders at its route with no console errors
- [ ] Visually matches the mockup image closely: same sections, same labels, same layout structure
- [ ] Status badges are visually distinct per state, matching the mockup colors
- [ ] Code passes `npm run lint` (oxlint) in frontend/
- [ ] Naming follows CODESTANDARD.md (PascalCase components, camelCase variables/functions, lowercase directories)
- [ ] No unrelated files modified outside frontend/
