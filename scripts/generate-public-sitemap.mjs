import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(process.cwd());
const base = 'https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/';
const sitemapPath = resolve(root, 'sitemap.xml');
const dataPath = resolve(root, 'website/src/data/public-seo.json');
const outputPath = resolve(root, 'dist/sitemap.xml');
const [source, dataSource] = await Promise.all([readFile(sitemapPath, 'utf8'), readFile(dataPath, 'utf8')]);
const data = JSON.parse(dataSource);
const urls = new Set([...source.matchAll(/<loc>([^<]+)<\/loc>/g)].map(match => match[1]));
const slugify = value => String(value || 'item').normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 70) || 'item';
for (const artist of data.artists || []) {
  if (artist?.id && artist?.name && /^[A-Za-z0-9_-]{1,80}$/.test(String(artist.id))) urls.add(base + 'artists/' + encodeURIComponent(String(artist.id)) + '-' + slugify(artist.name) + '/');
}
for (const release of data.releases || []) {
  if (release?.id && release?.title && /^[A-Za-z0-9_-]{1,80}$/.test(String(release.id))) urls.add(base + 'releases/' + encodeURIComponent(String(release.id)) + '-' + slugify(release.title) + '/');
}
const xml = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
  [...urls].sort().map(url => '  <url><loc>' + url.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</loc></url>').join('\n') +
  '\n</urlset>\n';
await writeFile(outputPath, xml, 'utf8');
console.log(`HTE sitemap generated with ${urls.size} public URLs.`);
