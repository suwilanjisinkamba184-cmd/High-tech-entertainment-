#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
rm -rf dist
mkdir -p dist
cp -R assets admin account dist/
cp index.html supabase-config.js robots.txt sitemap.xml account-deletion.html privacy-policy.html high-tech-logo.svg dist/
for file in google31fdb4754075f7bd.html googled2aa97c2eb951371.html; do
  if [ -s "$file" ]; then cp "$file" dist/; fi
done
touch dist/.nojekyll
test -s dist/index.html
test -s dist/robots.txt
test -s dist/sitemap.xml
test -s dist/supabase-config.js
test -s dist/assets/site.css
test -s dist/admin/index.html
test -s dist/account/index.html
python -c "import xml.etree.ElementTree as ET; ET.parse('dist/sitemap.xml')"
echo "Cloudflare Pages static output prepared in dist/"
