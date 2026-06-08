import { expect, test } from '@playwright/test';

test('browse resources and open detail page', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /共享库/ })).toBeVisible();

  await page.getByRole('link', { name: '资源库' }).click();
  await expect(page.getByRole('heading', { name: '资源库' })).toBeVisible();
  await page.getByText('2026 考研政治历年真题完整版').first().click();
  await expect(page.getByRole('heading', { name: /考研政治/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /下载此附件/ }).first()).toBeVisible();
});

test('login writes a mock user session', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('账号').fill('demo_user');
  await page.getByLabel('密码').fill('password123');
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByText('资源分享论坛').first()).toBeVisible();
});
