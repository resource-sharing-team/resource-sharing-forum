import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { appendFileSync } from 'node:fs';
import { createServer } from 'vite';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const logFile = path.join(root, 'dev-server-run.log');

function log(message) {
  appendFileSync(logFile, `${new Date().toISOString()} ${message}\n`);
}

process.on('uncaughtException', (error) => {
  log(`uncaughtException: ${error.stack || error.message}`);
  process.exit(1);
});

process.on('unhandledRejection', (error) => {
  log(`unhandledRejection: ${error instanceof Error ? error.stack : String(error)}`);
  process.exit(1);
});

process.on('beforeExit', (code) => log(`beforeExit: ${code}`));
process.on('exit', (code) => log(`exit: ${code}`));

const server = await createServer({
  root,
  configFile: path.join(root, 'vite.config.ts'),
  server: {
    host: '127.0.0.1',
    port: 5173,
  },
});

await server.listen();
server.printUrls();
log('server listening on http://127.0.0.1:5173');

async function close() {
  log('received close signal');
  await server.close();
  process.exit(0);
}

process.on('SIGINT', close);
process.on('SIGTERM', close);

setInterval(() => {}, 60 * 60 * 1000);
await new Promise(() => {});
