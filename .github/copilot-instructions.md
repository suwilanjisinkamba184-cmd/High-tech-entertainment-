# High-Tech Entertainment — Copilot instructions

## Project context
- This repository contains the public music website, founder/admin dashboard, account pages, Supabase integration, and a Flutter Android application under `mobile-app/`.
- The website is a static HTML/CSS/JavaScript application with a small Node.js server; production deployment is configured for Netlify.
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
