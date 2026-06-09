import { describe, expect, it } from 'vitest';

import readme from '../../README.md?raw';
import backendIntegrationSpec from '../../e2e/backend-integration.spec.ts?raw';
import main from '../main.tsx?raw';

describe('frontend backend integration config', () => {
  it('documents real backend and mock-mode environment variables', () => {
    expect(readme).toContain('VITE_API_BASE_URL=http://localhost:8080');
    expect(readme).toContain('VITE_API_PREFIX=/api');
    expect(readme).toContain('VITE_ENABLE_MOCKS=false');
  });

  it('does not start MSW when a backend base URL is configured unless mocks are explicit', () => {
    expect(main).toContain("import.meta.env.VITE_API_BASE_URL");
    expect(main).toContain("mockMode === 'false'");
    expect(main).toContain("hasBackendBaseUrl && mockMode !== 'true'");
  });

  it('runs backend e2e assertions against the configured backend base URL', () => {
    expect(backendIntegrationSpec).toContain('process.env.VITE_API_BASE_URL');
    expect(backendIntegrationSpec).toContain('process.env.VITE_API_PREFIX');
    expect(backendIntegrationSpec).not.toContain("url.origin === 'http://127.0.0.1:18080'");
  });
});
