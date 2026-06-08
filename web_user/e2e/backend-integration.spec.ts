import { expect, test } from '@playwright/test';

const expectedApiBase = apiBaseFromEnvironment();

test('loads public resource data from configured backend without MSW', async ({ page }) => {
  const resourceResponse = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.origin === expectedApiBase.origin && url.pathname === `${expectedApiBase.pathname}/resources`;
  });

  await page.goto('/');
  const response = await resourceResponse;
  const body = await response.json();

  expect(response.status()).toBe(200);
  expect(response.headers()['x-trace-id']).toBeTruthy();
  expect(body.code).toBe(200);
  expect(body.data.list.length).toBeGreaterThan(0);
  await expect(page.getByText('可用资源')).toBeVisible();
});

function apiBaseFromEnvironment() {
  const origin = (process.env.VITE_API_BASE_URL || 'http://127.0.0.1:18080').trim().replace(/\/+$/, '');
  const prefix = normalizeApiPrefix(process.env.VITE_API_PREFIX || '/api');
  const apiBase = /\/api(?:\/v1)?$/.test(origin) ? origin : `${origin}${prefix}`;
  const url = new URL(apiBase);
  return {
    origin: url.origin,
    pathname: url.pathname.replace(/\/+$/, ''),
  };
}

function normalizeApiPrefix(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return '/api';
  return `/${trimmed.replace(/^\/+|\/+$/g, '')}`;
}
