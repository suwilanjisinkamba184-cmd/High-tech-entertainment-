import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(process.cwd());
const configPath = resolve(root, 'supabase-config.js');
const outputPath = resolve(root, 'website/src/data/public-seo.json');
const source = await readFile(configPath, 'utf8');
const url = source.match(/url:\s*['"]([^'"]+)['"]/)?.[1];
const key = source.match(/publishableKey:\s*['"]([^'"]+)['"]/)?.[1];
const empty = { artists: [], releases: [] };

async function fetchPublic(table, select) {
  if (!url || !key) return [];
  const endpoint = new URL('/rest/v1/' + table, url);
  endpoint.search = new URLSearchParams({
    select,
    is_published: 'eq.true',
    order: 'created_at.desc',
    limit: '100'
  }).toString();
  try {
    const response = await fetch(endpoint, {
      headers: { apikey: key, Authorization: 'Bearer ' + key, Accept: 'application/json' },
      signal: AbortSignal.timeout(12000)
    });
    if (!response.ok) {
      console.warn(`HTE SEO data: ${table} returned HTTP ${response.status}; dynamic pages will be skipped for this build.`);
      return [];
    }
    const rows = await response.json();
    if (!Array.isArray(rows)) return [];
    return rows.filter(row => row && row.id != null).map(row => {
      const clean = {};
      for (const field of ['id','name','title','bio','image_url','cover_url','release_date','spotify_url']) {
        if (typeof row[field] === 'string' || typeof row[field] === 'number') clean[field] = row[field];
      }
      clean.id = String(row.id);
      return clean;
    });
  } catch (error) {
    console.warn(`HTE SEO data: could not fetch ${table}; dynamic pages will be skipped for this build: ${error.message}`);
    return [];
  }
}

const data = {
  artists: await fetchPublic('artists', 'id,name,bio,image_url,created_at'),
  releases: await fetchPublic('releases', 'id,title,release_date,cover_url,spotify_url,created_at')
};
await writeFile(outputPath, JSON.stringify(data, null, 2) + '\n', 'utf8');
console.log(`HTE SEO data prepared: ${data.artists.length} public artist profiles and ${data.releases.length} public releases.`);
