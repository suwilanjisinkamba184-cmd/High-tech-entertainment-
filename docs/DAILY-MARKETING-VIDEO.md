# Daily High-Tech Entertainment video workflow

## Workflow
The repository workflow is `.github/workflows/daily-marketing-video.yml`. It can run on its configured schedule and through GitHub Actions manual dispatch. Check the workflow file for the exact schedule and inputs; scheduled GitHub Actions runs can be delayed and are not an exact-time guarantee.

## Output and dependencies
The workflow creates an original short vertical motion-graphics promotion using the approved `high-tech-logo.svg` and HTE black/gold/silver visual identity. It uses the rendering dependencies declared by the workflow (FFmpeg, Pillow and CairoSVG where installed) and saves the generated media/caption as a workflow artifact.

The current template-driven renderer is not a generative text-to-video service. Do not describe it as Google Flow/Veo or claim that it creates photorealistic AI scenes or generated narration unless a separate provider integration is configured and tested.

## Audio and publishing
The rendered video is silent unless the workflow source explicitly adds an audio stage. Facebook Page publishing is conditional on the required Meta Page credentials being configured. If the credentials are absent, rendering should still complete and publishing should be skipped safely.

Required publishing secrets, when enabled:
- `META_PAGE_ACCESS_TOKEN` — a valid Page Access Token with the required publishing permission.
- `META_PAGE_ID` — the intended HTE Facebook Page ID.

Never put either value in source control, workflow logs or chat. This workflow publishes organic Page content; it is not a paid advertising campaign manager.

## Verification
Inspect the latest Actions run and confirm the render and optional publish steps separately. A successful artifact build does not prove that the social account is connected or that a post was published. Check the actual Page post before reporting publication success.
