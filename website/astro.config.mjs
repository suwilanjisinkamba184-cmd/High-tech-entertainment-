import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://suwilanjisinkamba184-cmd.github.io',
  // GitHub Pages is canonical. Cloudflare previews use the root path.
  base: process.env.CF_PAGES === '1' ? '/' : '/High-tech-entertainment-',
  output: 'static',
  integrations: [react()],
  vite: { plugins: [tailwindcss()] },
  build: { format: 'directory' }
});
