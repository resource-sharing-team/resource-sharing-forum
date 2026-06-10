import { expect, test } from '@playwright/test';

test('browse resources and open detail page', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('最新资源')).toBeVisible();

  await page.getByRole('link', { name: '资源库' }).click();
  await expect(page.getByRole('heading', { name: '资源库' })).toBeVisible();
  await expect(page.getByText('查看详情')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /收藏|点赞/ })).toHaveCount(0);
  await page.locator('.resource-card').first().click();
  await expect(page.getByText(/考研政治/).first()).toBeVisible();
  await expect(page.getByRole('button', { name: /下载此附件/ }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: /点赞/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /收藏/ })).toBeVisible();
});

test('login writes a mock user session', async ({ page }) => {
  await page.goto('/login');
  await page.getByPlaceholder('请输入用户名或邮箱').fill('demo_user');
  await page.getByPlaceholder('请输入登录密码').fill('password123');
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByText('资源分享论坛').first()).toBeVisible();
});

test('profile lists support item actions and navigation', async ({ page }) => {
  await page.goto('/profile');
  await expect(page.locator('.right-content .card-title', { hasText: '个人资料' })).toBeVisible();

  await page.getByText('我的收藏').click();
  await expect(page.getByText('UI 设计全套 Figma 模板合集')).toBeVisible();
  await expect(page.getByRole('button', { name: '取消收藏' }).first()).toBeVisible();
  await page.getByRole('button', { name: '取消收藏' }).first().click();
  await expect(page.getByText('UI 设计全套 Figma 模板合集')).toBeHidden();

  await page.getByText('我的点赞').click();
  await expect(page.getByRole('button', { name: '取消点赞' }).first()).toBeVisible();

  await page.getByText('我的求资源').click();
  await expect(page.getByText('求 2026 教资面试结构化真题及解析')).toBeVisible();
  await page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: /删除/ }).first().click();
  await expect(page.getByText('求 2026 教资面试结构化真题及解析')).toBeHidden();
});

test('profile tab survives page refresh', async ({ page }) => {
  await page.goto('/profile?tab=my-fav');
  await expect(page.locator('.right-content .card-title', { hasText: '我的收藏' })).toBeVisible();

  await page.reload();
  await expect(page.locator('.right-content .card-title', { hasText: '我的收藏' })).toBeVisible();

  await page.getByText('安全中心').click();
  await expect(page).toHaveURL(/tab=security/);
  await page.reload();
  await expect(page.locator('.right-content .card-title', { hasText: '修改密码' })).toBeVisible();
});

test('comments support second-level replies and reports', async ({ page }) => {
  await page.goto('/resources/1');
  await expect(page.getByText('评论与回复')).toBeVisible();

  await page.locator('.comment-item').first().getByRole('button', { name: '回复' }).click();
  await page.locator('.reply-input').fill('感谢补充，我也测试了一下。');
  await page.getByRole('button', { name: '发布回复' }).click();
  await expect(page.getByText('感谢补充，我也测试了一下。')).toBeVisible();
  await expect(page.getByText(/回复 资料核对员/)).toBeVisible();

  await page.locator('.comment-item').first().getByRole('button', { name: '举报' }).click();
  await expect(page.getByText('我要举报/投诉')).toBeVisible();
  await page.getByPlaceholder('请详细说明违规、侵权或不实内容').fill('这条评论存在明显无关内容，请管理员核查。');
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('我要举报/投诉')).toBeHidden();
});
