#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Astro owns the public homepage. Shared account, Founder Studio, and legacy
# Supabase modules are copied alongside it so their existing routes survive.
mkdir -p website/public
cp high-tech-logo.svg website/public/high-tech-logo.svg
cp supabase-config.js website/public/supabase-config.js

npm --prefix website install --no-audit --no-fund
npm --prefix website run check
npm --prefix website run build

rm -rf dist
mkdir -p dist
cp -R website/dist/. dist/
cp -R assets admin account dist/
cp app-download.html robots.txt sitemap.xml account-deletion.html privacy-policy.html high-tech-logo.svg dist/

for file in terms-of-service.html; do
  if [ ! -s "$file" ]; then
    echo "Required source page is missing: $file" >&2
    exit 1
  fi
  install -D -m 0644 "$file" "dist/$file"
done

for file in google31fdb4754075f7bd.html googled2aa97c2eb951371.html; do
  if [ -s "$file" ]; then cp "$file" dist/; fi
done

touch dist/.nojekyll

for file in index.html app-download.html robots.txt sitemap.xml supabase-config.js account-deletion.html privacy-policy.html terms-of-service.html admin/index.html account/index.html assets/site.css assets/app.js assets/videos.js assets/booking-confirmation.aac high-tech-logo.svg; do
  test -s "dist/$file" || { echo "Missing required build output: dist/$file" >&2; exit 1; }
done

python - <<'PY'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path("dist")
ET.parse(root / "sitemap.xml")
missing = []
for page in root.rglob("*.html"):
    source = page.read_text(encoding="utf-8")
    for value in re.findall(r'''(?:href|src)=["']([^"'#]+)["']''', source, flags=re.I):
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
    source = stylesheet.read_text(encoding="utf-8")
    for value in re.findall(r'''url\(\s*['"]?([^'")]+)['"]?\s*\)''', source, flags=re.I):
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
    raise SystemExit("\\n".join(missing))
print("Astro output, sitemap XML, local HTML references, and CSS assets are valid.")
PY

echo "Astro + React website build output prepared in dist/"
