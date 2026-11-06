#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

rm -rf dist
mkdir -p dist

cp -R assets admin account dist/
cp index.html app-download.html supabase-config.js robots.txt sitemap.xml account-deletion.html privacy-policy.html high-tech-logo.svg dist/

# Keep the Terms page as an explicit build input so the homepage footer link
# can never point to a file omitted from the published dist directory.
if [ ! -s "terms-of-service.html" ]; then
  echo "Required source page is missing: terms-of-service.html" >&2
  exit 1
fi
install -D -m 0644 "terms-of-service.html" "dist/terms-of-service.html"
if [ ! -s "dist/terms-of-service.html" ]; then
  echo "Failed to publish dist/terms-of-service.html" >&2
  exit 1
fi

for file in google31fdb4754075f7bd.html googled2aa97c2eb951371.html; do
  if [ -s "$file" ]; then cp "$file" dist/; fi
done

touch dist/.nojekyll

# Required output checks shared by GitHub Pages and Cloudflare Pages.
for file in index.html app-download.html robots.txt sitemap.xml supabase-config.js account-deletion.html privacy-policy.html terms-of-service.html admin/index.html account/index.html assets/site.css assets/app.js assets/videos.js assets/booking-confirmation.aac; do
  test -s "dist/$file" || { echo "Missing required build output: dist/$file" >&2; exit 1; }
done

python - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path("dist")
ET.parse(root / "sitemap.xml")

# Verify that every local HTML/CSS asset reference resolves in the published tree.
import re
missing = []
for page in root.rglob("*.html"):
    text = page.read_text(encoding="utf-8")
    for value in re.findall(r'''(?:href|src)=["']([^"'#]+)["']''', text, flags=re.I):
        if re.match(r"^(?:https?:|mailto:|tel:|data:|javascript:|//)", value, flags=re.I):
            continue
        target = value.split("?", 1)[0].split("#", 1)[0]
        if not target:
            continue
        resolved = (page.parent / target.lstrip("/") if not target.startswith("/") else root / target.lstrip("/")).resolve()
        if target.startswith("../"):
            resolved = (page.parent / target).resolve()
        try:
            resolved.relative_to(root.resolve())
        except ValueError:
            missing.append(f"{page}: reference escapes build output: {value}")
            continue
        if not resolved.exists() and not (resolved / "index.html").exists():
            missing.append(f"{page}: missing local reference: {value}")

for stylesheet in root.rglob("*.css"):
    text = stylesheet.read_text(encoding="utf-8")
    for value in re.findall(r'''url\(\s*['"]?([^'")]+)['"]?\s*\)''', text, flags=re.I):
        value = value.strip()
        if re.match(r"^(?:https?:|data:|#|//)", value, flags=re.I):
            continue
        target = value.split("?", 1)[0].split("#", 1)[0]
        resolved = (stylesheet.parent / target.lstrip("/") if not target.startswith("/") else root / target.lstrip("/")).resolve()
        try:
            resolved.relative_to(root.resolve())
        except ValueError:
            missing.append(f"{stylesheet}: CSS reference escapes build output: {value}")
            continue
        if not resolved.exists():
            missing.append(f"{stylesheet}: missing CSS reference: {value}")

if missing:
    raise SystemExit("\n".join(missing))
print("Sitemap XML, local HTML references, and CSS assets are valid.")
PY

echo "Website build output prepared in dist/"
