import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './client';
import {
  getDemands,
  getResources,
  getUserFavorites,
  getUserLikes,
  getUserLoginRecords,
  getUserRequests,
  getUserResources,
  reportContent,
  userCenterBasePath,
} from './endpoints';

describe('user center endpoints', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses the versioned backend user routes when the app is configured with /api', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 20 },
    });

    await getUserResources({ page: 1, pageSize: 20 });
    await getUserRequests({ page: 1, pageSize: 20 });
    await getUserFavorites({ page: 1, pageSize: 20 });
    await getUserLikes({ page: 1, pageSize: 20 });
    await getUserLoginRecords({ page: 1, pageSize: 20 });

    expect(userCenterBasePath).toBe('/v1/user');
    expect(get).toHaveBeenNthCalledWith(1, '/v1/user/resources', expect.any(Object));
    expect(get).toHaveBeenNthCalledWith(2, '/v1/user/requests', expect.any(Object));
    expect(get).toHaveBeenNthCalledWith(3, '/v1/user/favorites', expect.any(Object));
    expect(get).toHaveBeenNthCalledWith(4, '/v1/user/likes', expect.any(Object));
    expect(get).toHaveBeenNthCalledWith(5, '/v1/user/login-records', expect.any(Object));
  });
});

describe('report endpoints', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sends title and proof summary for standard reports', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { ok: true },
    });

    await reportContent({
      target: 'RESOURCE',
      targetId: 1,
      type: 'RESOURCE',
      title: '资源举报：测试资源',
      reason: '这是一次举报原因',
      proofSummary: '这是证据摘要',
    });

    expect(post).toHaveBeenCalledWith('/reports', {
      target: 'RESOURCE',
      targetType: 'RESOURCE',
      targetId: 1,
      type: 'RESOURCE',
      title: '资源举报：测试资源',
      reason: '这是一次举报原因',
      proofSummary: '这是证据摘要',
      contactEmail: undefined,
    });
  });

  it('sends copyright complaints with proofSummary instead of merging proof into reason', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { ok: true },
    });

    await reportContent({
      target: 'COPYRIGHT',
      targetId: 1,
      type: 'COPYRIGHT',
      title: '版权投诉：测试资源',
      reason: '这是版权投诉原因',
      proofSummary: '这是权属证明摘要',
    });

    expect(post).toHaveBeenCalledWith('/reports/copyright-complaints', {
      targetId: 1,
      title: '版权投诉：测试资源',
      reason: '这是版权投诉原因',
      proofSummary: '这是权属证明摘要',
      contactEmail: undefined,
    });
  });
});

describe('listing endpoints', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sends a first-level category without treating it as a leaf categoryId', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 10 },
    });

    await getResources({ page: 1, pageSize: 10, cate1: '1' });

    expect(get).toHaveBeenCalledWith('/resources', {
      params: expect.objectContaining({
        category1: '1',
        cate1: '1',
        categoryId: undefined,
      }),
    });
  });

  it('sends the second-level category as categoryId for backend leaf filtering', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 10 },
    });

    await getResources({ page: 1, pageSize: 10, cate1: '1', cate2: '11' });

    expect(get).toHaveBeenCalledWith('/resources', {
      params: expect.objectContaining({
        category1: '1',
        category2: '11',
        categoryId: '11',
      }),
    });
  });

  it('sends demand reward filtering through rewardRange', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { items: [], total: 0, page: 1, size: 5 },
    });

    await getDemands({ page: 1, pageSize: 5, rewardRange: '0-100' });

    expect(get).toHaveBeenCalledWith('/requests', {
      params: expect.objectContaining({
        rewardRange: '0-100',
        points: '0-100',
        pointsFilter: '0-100',
      }),
    });
  });
});
