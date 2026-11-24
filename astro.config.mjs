import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://suwilanjisinkamba184-cmd.github.io/High-tech-entertainment-/',
  // GitHub Pages is the only supported production host.
  base: '/High-tech-entertainment-/',
  output: 'static',
  integrations: [react()],
  vite: { plugins: [tailwindcss()] },
  build: { format: 'directory' }
});
