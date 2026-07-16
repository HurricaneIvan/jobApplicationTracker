import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import os from 'node:os';
import path from 'node:path';

// Keep the dep-optimizer cache OUT of the project tree. When the repo lives under a
// synced folder (OneDrive/Dropbox), the sync filter locks esbuild's temp files mid-write
// and dependency optimization fails with "Access is denied". A temp-dir cache avoids that.
// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  cacheDir: path.join(os.tmpdir(), 'vite-job-application-tracker'),
  server: {
    port: 5173,
  },
});
