import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://suwilanjisinkamba184-cmd.github.io',
  base: process.env.CF_PAGES === '1' || process.env.NETLIFY === 'true' ? '/' : '/High-tech-entertainment-',
  output: 'static',
  integrations: [react()],
  vite: { plugins: [tailwindcss()] },
  build: { format: 'directory' }
});
