# Daily High-Tech Entertainment video workflow

This workflow creates one original 10-second vertical motion-graphics promotional video every day at 09:00 Zambia time (07:00 UTC), and can also be run manually from GitHub Actions.

## What it does

- Uses the official `high-tech-logo.svg` and the existing black / metallic-gold / silver brand palette.
- Produces a fresh daily variation of the HTE brand message using original animated graphics.
- The initial MP4 is silent: it does not yet add narration or a music track.
- Uses FFmpeg, Pillow and CairoSVG; no paid AI-video API, HeyGen API key or ElevenLabs API key is required.
- Saves the MP4 and caption as a GitHub Actions artifact for 30 days.
- Publishes to the Facebook Page automatically when both Page secrets are configured.

## Important limitation

This is a free, template-driven motion-graphics video maker, not text-to-video AI like Google Flow or Veo. It does not create new photorealistic AI scenes or generated narration. Those services do not provide an unlimited free unattended API.

## Enable Facebook auto-publishing

In GitHub, open the repository Settings → Secrets and variables → Actions and add:

- `META_PAGE_ACCESS_TOKEN`: a valid Facebook **Page Access Token** with permission to publish Page content.
- `META_PAGE_ID`: the ID of the **High-Tech Entertainment** Facebook Page.

Never put either value in source code, a commit, or a chat message. Do not use a User Access Token in place of a Page Access Token.

If either secret is absent, the workflow still creates the video artifact but safely skips publishing. This workflow publishes organic Page video posts; it does not create paid Meta Ads campaigns.

## Monitoring

Open the repository Actions tab and select **Daily High-Tech cinematic promo**. A successful run must show the render step and, once configured, the Facebook publishing step. GitHub scheduled runs can be delayed and are not a guaranteed exact-time scheduler.
