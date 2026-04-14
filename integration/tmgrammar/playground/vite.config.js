// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import { defineConfig } from 'vite';
import path from 'path';
import fs from 'fs';

const tmgrammarRoot = path.resolve(__dirname, '..');
const vespaRoot = path.resolve(tmgrammarRoot, '..', '..');
const sdFilesDir = path.join(vespaRoot, 'integration/schema-language-server/language-server/src/test/sdfiles');

export default defineConfig({
  server: {
    fs: {
      allow: [tmgrammarRoot, sdFilesDir],
    },
  },
  publicDir: false,
  plugins: [
    {
      name: 'serve-repo-files',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          let filePath = null;
          if (req.url.startsWith('/repo/')) {
            filePath = path.join(tmgrammarRoot, req.url.slice('/repo/'.length));
          } else if (req.url.startsWith('/sdfiles/')) {
            filePath = path.join(sdFilesDir, req.url.slice('/sdfiles/'.length));
          }
          if (filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
            res.setHeader('Content-Type', 'application/octet-stream');
            fs.createReadStream(filePath).pipe(res);
            return;
          }
          next();
        });
      },
    },
  ],
});
