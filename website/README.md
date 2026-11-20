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

GitHub Pages remains the canonical production host. Cloudflare is used only for root-path preview builds.


## Search discovery and public content

- Dedicated service landing pages live in `website/src/pages/services/`.
- Editorial resources live in `website/src/pages/journal/`.
- Published artist and release profile routes are generated from the public Supabase catalogue at build time:
  - `scripts/generate-public-seo-data.mjs` fetches only rows where `is_published = true`.
  - `website/src/pages/artists/[id]-[slug].astro` and `website/src/pages/releases/[id]-[slug].astro` render those public records as crawlable static pages.
  - `scripts/generate-public-sitemap.mjs` adds generated profile URLs to the published sitemap.
- If the public Supabase REST API is unavailable during a build, the build continues with the static service and journal pages; dynamic profile pages are skipped for that build and will be retried on the next build.
- Canonical URLs, Open Graph metadata and Twitter card metadata are configured through `website/src/layouts/SiteLayout.astro`.
- External fonts and the Supabase browser SDK are opt-in layout resources. The homepage enables them; static editorial and service pages do not download the Supabase SDK.
- To add a public service or journal page, create an Astro page under the matching directory and add its canonical URL to the root `sitemap.xml`.
