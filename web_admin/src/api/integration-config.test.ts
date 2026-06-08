import { describe, expect, it } from 'vitest';

import viteConfig from '../../vite.config.ts?raw';

describe('admin frontend integration config', () => {
  it('proxies local api requests to the backend dev server', () => {
    expect(viteConfig).toContain("'/api'");
    expect(viteConfig).toContain("process.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'");
    expect(viteConfig).toContain('changeOrigin: true');
  });
});
