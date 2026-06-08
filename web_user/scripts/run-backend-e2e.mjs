import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const playwrightCli = path.join(root, 'node_modules', '@playwright', 'test', 'cli.js');
const frontendPort = 5173;
const backendUrl = process.env.VITE_API_BASE_URL || 'http://127.0.0.1:18080';

async function assertBackendReady() {
  const response = await fetch(`${backendUrl}/api/health`);
  if (!response.ok) {
    throw new Error(`Backend health check failed with HTTP ${response.status}`);
  }
}

await assertBackendReady();

process.env.VITE_API_BASE_URL = backendUrl;
process.env.VITE_API_PREFIX = process.env.VITE_API_PREFIX || '/api';
process.env.VITE_ENABLE_MOCKS = 'false';

const server = await createServer({
  root,
  configFile: path.join(root, 'vite.config.ts'),
  server: {
    host: '127.0.0.1',
    port: frontendPort,
  },
});

await server.listen();
server.printUrls();

const env = {
  ...process.env,
  PLAYWRIGHT_BASE_URL: `http://127.0.0.1:${frontendPort}`,
  PLAYWRIGHT_BACKEND_INTEGRATION: 'true',
};

const code = await new Promise((resolve) => {
  const child = spawn(
    process.execPath,
    [
      playwrightCli,
      'test',
      'e2e/backend-integration.spec.ts',
      '--workers=1',
      '--reporter=list',
      '--config',
      'playwright.config.ts',
    ],
    {
      cwd: root,
      env,
      stdio: 'inherit',
    },
  );
  child.on('close', resolve);
});

await server.close();
process.exit(Number(code));
