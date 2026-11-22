import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, relative, dirname, sep } from 'node:path';

const root = resolve(process.cwd());
const dist = resolve(root, 'dist');
const base = 'https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/';
const outputPath = resolve(dist, 'sitemap.xml');
const urls = new Set();

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = resolve(directory, entry.name);
    if (entry.isDirectory()) files.push(...await walk(fullPath));
    else if (entry.isFile() && entry.name.toLowerCase().endsWith('.html')) files.push(fullPath);
  }
  return files;
}

function canonicalFrom(html) {
  const tag = html.match(/<link\b(?=[^>]*\brel=["']canonical["'])[^>]*>/i)?.[0];
  return tag?.match(/\bhref=["']([^"']+)["']/i)?.[1] || null;
}

function isIndexable(html) {
  const tag = html.match(/<meta\b(?=[^>]*\bname=["']robots["'])[^>]*>/i)?.[0] || '';
  return !/\bnoindex\b/i.test(tag);
}

for (const file of await walk(dist)) {
  const path = relative(dist, file).split(sep).join('/');
  // Google Search Console HTML verification tokens must remain published, but are not landing pages.
  if (/^google[0-9a-f]{16,}\.html$/i.test(path)) continue;
  if (path === '404.html' || path.startsWith('admin/') || path.startsWith('account/')) continue;
  const html = await readFile(file, 'utf8');
  if (!isIndexable(html)) continue;
  const canonical = canonicalFrom(html);
  let url;
  if (canonical) {
    if (!canonical.startsWith(base)) throw new Error(`Off-site canonical on ${path}: ${canonical}`);
    url = canonical;
  } else if (path === 'index.html') {
    url = base;
  } else if (path.endsWith('/index.html')) {
    url = base + dirname(path).replaceAll('\\', '/') + '/';
  } else {
    url = base + path;
  }
  urls.add(url);
}

if (!urls.has(base)) throw new Error('The official homepage is missing from the generated sitemap.');
const escapeXml = (value) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const xml = '<?xml version="1.0" encoding="UTF-8"?>\n' +
  '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
  [...urls].sort().map((url) => `  <url><loc>${escapeXml(url)}</loc></url>`).join('\n') +
  '\n</urlset>\n';
await writeFile(outputPath, xml, 'utf8');
console.log(`HTE sitemap generated from ${urls.size} indexable public HTML pages.`);
