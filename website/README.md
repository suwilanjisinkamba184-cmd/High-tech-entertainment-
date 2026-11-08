# Website source of truth

The public homepage is built from **Astro**, not the legacy root `index.html`.

- Homepage markup and navigation: `website/src/pages/index.astro`
- Homepage layout and Supabase config bootstrap: `website/src/layouts/SiteLayout.astro`
- Homepage styling and responsive UI: `website/src/styles/global.css`
- Interactive public catalogue: `website/src/components/PublicCatalogue.tsx`
- Public booking and feedback forms: `website/src/components/PublicBookingForm.tsx` and `PublicFeedbackForm.tsx`
- Member account UI: `account/index.html` and `assets/account-experience.css`
- Account, authentication and Founder Studio behavior: `assets/app.js`

The GitHub Pages workflow runs `scripts/build-website.sh` and publishes `dist/`. Changes made only to the legacy root `index.html` do not control the deployed homepage. The build now checks for Astro homepage markers so this mismatch fails CI instead of silently publishing the wrong UI.

GitHub Pages remains the canonical production host. Cloudflare is used only for root-path preview builds; Netlify is not part of this deployment.
