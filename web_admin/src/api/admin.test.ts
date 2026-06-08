import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiClient = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

describe('admin api pagination adapter', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllEnvs();
    vi.stubEnv('VITE_ENABLE_MOCKS', 'false');
    apiClient.get.mockReset();
    apiClient.post.mockReset();
    vi.doMock('./client', () => ({ apiClient }));
  });

  it('paginates legacy content aggregate responses by requested section', async () => {
    apiClient.get.mockResolvedValue({
      data: {
        auditRows: [{ id: 'R001', title: '待审核资源', user: 'user001', status: '待审核' }],
        resourceRows: [
          { id: 'R002', title: '已发布资源', user: 'user002', status: '已发布' },
          { id: 'R003', title: '已下架资源', user: 'user003', status: '已下架' },
        ],
        requestRows: [],
        commentRows: [],
      },
    });

    const { getAdminContentPage } = await import('./admin');
    const result = await getAdminContentPage('resource', { page: '1', size: '1' });

    expect(result.total).toBe(2);
    expect(result.page).toBe(1);
    expect(result.size).toBe(1);
    expect(result.list).toEqual([{ id: 'R002', title: '已发布资源', user: 'user002', status: '已发布' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/admin/content', {
      params: { page: '1', size: '1', section: 'resource' },
    });
  });

  it('paginates legacy compliance aggregate responses by requested section', async () => {
    apiClient.get.mockResolvedValue({
      data: {
        reports: [],
        complaints: [
          { id: 'B001', resourceId: 'R006', resourceName: '侵权课件', complainant: '版权方A', status: '待审核' },
        ],
        appeals: [],
      },
    });

    const { getAdminCompliancePage } = await import('./admin');
    const result = await getAdminCompliancePage('copyright', { page: '1', size: '10' });

    expect(result.total).toBe(1);
    expect(result.list?.[0]).toMatchObject({ id: 'B001', resourceName: '侵权课件' });
  });

  it('paginates legacy catalog aggregate responses and keeps numeric metadata valid', async () => {
    apiClient.get.mockResolvedValue({
      data: {
        categories: [{ id: 'F001', name: '设计素材', type: '一级分类', parent: '-', relationCount: 2, status: '启用' }],
        tags: [{ id: 'T001', name: 'UI', type: '标签', parent: '-', relationCount: 3, status: '启用' }],
        rows: [],
      },
    });

    const { getAdminCatalogPage } = await import('./admin');
    const result = await getAdminCatalogPage('all', { page: '1', size: '10' });

    expect(result.total).toBe(2);
    expect(result.page).toBe(1);
    expect(result.size).toBe(10);
    expect(result.list?.map((item) => item.id)).toEqual(['F001', 'T001']);
  });

  it('passes catalog level filters to the real section endpoint', async () => {
    apiClient.get.mockResolvedValue({
      data: {
        section: 'category',
        total: 1,
        list: [{ id: 'F003', name: 'UI设计', type: '二级分类', parent: '设计素材', relationCount: 4, status: '启用' }],
        page: 1,
        size: 10,
      },
    });

    const { getAdminCatalogPage } = await import('./admin');
    const result = await getAdminCatalogPage('category', { page: '1', size: '10', level: '2' });

    expect(result.total).toBe(1);
    expect(result.list?.[0]).toMatchObject({ id: 'F003', type: '二级分类' });
    expect(apiClient.get).toHaveBeenCalledWith('/admin/catalog', {
      params: { page: '1', size: '10', level: '2', section: 'category' },
    });
  });

  it('uses catalog options and normative tag endpoints', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: {
        firstLevelCategories: [{ id: 1, name: '文档资料', status: 'ENABLED' }],
        secondLevelCategories: [],
        resourceTypes: [{ code: 'DOCUMENT', name: '文档' }],
        tagCandidates: [{ name: '文档资料', source: '一级分类', exists: false, status: 'MISSING' }],
        missingTags: [{ name: '文档资料', source: '一级分类', exists: false, status: 'MISSING' }],
      },
    });
    apiClient.post.mockResolvedValueOnce({ data: { id: 12, name: '文档资料' } });
    apiClient.post.mockResolvedValueOnce({ data: { createdCount: 1, createdTags: ['文档资料'] } });

    const { getAdminCatalogOptions, createTag, backfillNormativeTags } = await import('./admin');

    await expect(getAdminCatalogOptions()).resolves.toMatchObject({
      firstLevelCategories: [{ id: 1, name: '文档资料', status: 'ENABLED' }],
      tagCandidates: [{ name: '文档资料', source: '一级分类' }],
    });
    await createTag('文档资料');
    await expect(backfillNormativeTags()).resolves.toMatchObject({ createdCount: 1 });

    expect(apiClient.get).toHaveBeenCalledWith('/admin/catalog/options');
    expect(apiClient.post).toHaveBeenNthCalledWith(1, '/admin/tags', { name: '文档资料' });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, '/admin/tags/backfill');
  });

  it('sends explicit first and second level category create payloads', async () => {
    apiClient.post.mockResolvedValue({ data: { id: 20 } });

    const { createCategory } = await import('./admin');
    await createCategory({ name: '资料库', level: 1, sortOrder: 8 });
    await createCategory({ name: '课程资料', level: 2, parentId: 1, sortOrder: 2 });

    expect(apiClient.post).toHaveBeenNthCalledWith(1, '/admin/categories', { name: '资料库', level: 1, sortOrder: 8 });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, '/admin/categories', { name: '课程资料', level: 2, parentId: 1, sortOrder: 2 });
  });
});
