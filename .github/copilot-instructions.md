# High-Tech Entertainment — Copilot instructions

## Project context
- This repository contains the public music website, founder/admin dashboard, account pages, Supabase integration, and a Flutter Android application under `mobile-app/`.
- The public website is a static HTML/CSS/JavaScript site deployed through GitHub Pages (`.github/workflows/publish-github-pages.yml`). The Node.js server is for supported local/server workflows; do not silently change the production deployment target.
- Supabase provides authentication, Postgres data, and Storage. The browser configuration must contain only the project URL and publishable/anon key.

## Required working practices
- Inspect the existing implementation and repository structure before changing files. Preserve existing behavior and deployment configuration unless the task explicitly requests a change.
- Never add Supabase service-role keys, secret API keys, signing keystores, passwords, or private credentials to tracked files, logs, or client-side code.
- Treat Supabase Row Level Security as the security boundary; do not rely on hiding admin UI controls for authorization.
- Keep the public website, admin dashboard, and Flutter application consistent with the existing brand and responsive design.
- Test relevant behavior after changes. Report what was actually tested and distinguish verified results from recommendations.
- Prefer accessible controls, keyboard support, reduced-motion support, responsive layouts, and efficient animations. Do not add motion merely for decoration.
- Avoid introducing new dependencies when the existing stack can solve the problem.

## Emil Kowalski design-engineering skills
Use the project skills in `.github/skills/` when relevant:
- `emil-design-eng` for overall UI craft and interaction quality.
- `animate`, `review-animations`, `improve-animations`, and `animation-performance` for motion implementation, audits, and performance.
- `mobile-native` for mobile-browser usability and touch behavior on the website.
- `break-ui` for edge-case and stress testing of UI components.
- `prototype` for comparing distinct UI directions when requested.
- `apple-design` for gesture, typography, accessibility, and motion principles.
- `pick-ui-library` when selecting a frontend component library.

For the Flutter app, translate applicable design principles into Flutter-native patterns; do not apply React Native or Expo-specific implementation advice to Flutter code.

## Impeccable and frontend quality skills
- Use `.github/skills/impeccable/SKILL.md` as the primary design director for website, admin, account, and native app interface work. Its command references are in `.github/skills/impeccable/reference/`.
- For website redesigns and polish, follow Impeccable's Persuade/Experience mode, use `PRODUCT.md` and `DESIGN.md`, and apply `craft-floor.md`, `critique.md`, `audit.md`, `polish.md`, `harden.md`, `adapt.md`, and `typeset.md` as relevant.
- Use `emil-design-eng` for high-fidelity visual craft; `break-ui` for long titles, missing covers, empty catalogues, unusual text, and small-screen stress cases.
- Use `mobile-native` for the responsive website on real phones; use `mobile-native` principles only where applicable and use Flutter-native patterns for the Flutter app.
- Use `apple-design`, `animate`, `improve-animations`, `review-animations`, and `animation-performance` for purposeful motion, reduced-motion behavior, and frame-budget decisions.
- Use `pick-ui-library` before introducing new frontend component dependencies; prefer the existing vanilla HTML/CSS/JS and Flutter stacks when they are sufficient.
- Use `prototype` when materially different visual directions need comparison. Do not install libraries or add dependencies just to imitate a visual style.
- Preserve a single shared HTE brand identity across website and app, while allowing each surface to use the interaction patterns appropriate to its purpose.
