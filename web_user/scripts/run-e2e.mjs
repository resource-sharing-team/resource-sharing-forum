import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const playwrightCli = path.join(root, 'node_modules', '@playwright', 'test', 'cli.js');
const devUrl = 'http://127.0.0.1:5173';

async function hasRunningServer() {
  try {
    const response = await fetch(devUrl);
    return response.ok;
  } catch {
    return false;
  }
}

let server = null;
if (!(await hasRunningServer())) {
  server = await createServer({
    root,
    configFile: path.join(root, 'vite.config.ts'),
    server: {
      host: '127.0.0.1',
      port: 5173,
    },
  });
  await server.listen();
  server.printUrls();
}

const code = await new Promise((resolve) => {
  const child = spawn(process.execPath, [playwrightCli, 'test', '--workers=1', '--reporter=list'], {
    cwd: root,
    stdio: 'inherit',
  });
  child.on('close', resolve);
});

if (server) await server.close();
process.exit(Number(code));
