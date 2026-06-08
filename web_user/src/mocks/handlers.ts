import { http, HttpResponse } from 'msw';
import { db } from './db';
import { filterDemands, filterResources, paginate } from '../utils/listing';
import type { ListParams, ResourceAttachment } from '../types';

function paramsFromUrl(request: Request): ListParams {
  const url = new URL(request.url);
  const params = Object.fromEntries(url.searchParams.entries());
  return {
    ...params,
    page: params.page ? Number(params.page) : 1,
    pageSize: params.pageSize ? Number(params.pageSize) : 10,
  };
}

function uploadedAttachments(formData: FormData): ResourceAttachment[] {
  const files = formData.getAll('files');
  if (!files.length) {
    return [
      {
        id: 1,
        name: String(formData.get('fileName') || 'uploaded-file.zip'),
        size: '待处理',
        type: '文件',
        downloads: 0,
      },
    ];
  }

  return files.map((file, index) => {
    const fileName = file instanceof File ? file.name : `附件 ${index + 1}`;
    return {
      id: index + 1,
      name: fileName,
      size: '待处理',
      type: fileName.includes('.') ? fileName.split('.').pop()?.toUpperCase() || '文件' : '文件',
      downloads: 0,
    };
  });
}

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { account?: string };
    return HttpResponse.json({
      token: 'mock-user-token',
      user: { ...db.user, username: body.account || db.user.username },
    });
  }),

  http.post('/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { username: string; email: string };
    db.user = { ...db.user, username: body.username, nickname: body.username, email: body.email, emailVerified: true };
    return HttpResponse.json({ token: 'mock-user-token', user: db.user }, { status: 201 });
  }),

  http.post('/api/auth/reset-password', () => HttpResponse.json({ ok: true })),

  http.get('/api/me', () => HttpResponse.json(db.user)),

  http.put('/api/me', async ({ request }) => {
    const body = (await request.json()) as Partial<typeof db.user>;
    db.user = { ...db.user, ...body };
    return HttpResponse.json(db.user);
  }),

  http.post('/api/me/password', () => {
    db.user.passwordUpdatedAt = new Date().toISOString().slice(0, 10);
    return HttpResponse.json({ ok: true, passwordUpdatedAt: db.user.passwordUpdatedAt });
  }),

  http.post('/api/me/email', async ({ request }) => {
    const body = (await request.json()) as { email: string };
    db.user = { ...db.user, email: body.email, emailVerified: true };
    return HttpResponse.json(db.user);
  }),

  http.get('/api/me/summary', () => {
    const favorites = db.resources.filter((item) => item.favorited);
    const likes = db.resources.filter((item) => item.liked);
    return HttpResponse.json({
      ...db.profileSummary,
      favorites,
      likes,
      resources: db.resources.slice(0, 3),
      demands: db.demands.slice(0, 3),
    });
  }),

  http.get('/api/resources', ({ request }) => {
    const params = paramsFromUrl(request);
    return HttpResponse.json(paginate(filterResources(db.resources, params), params.page, params.pageSize));
  }),

  http.get('/api/resources/:id', ({ params }) => {
    const resource = db.resources.find((item) => item.id === Number(params.id));
    if (!resource) return HttpResponse.json({ message: '资源不存在' }, { status: 404 });
    return HttpResponse.json({ resource, comments: db.resourceComments });
  }),

  http.post('/api/resources', async ({ request }) => {
    const formData = await request.formData();
    const attachments = uploadedAttachments(formData);
    const resource = {
      id: db.resources.length + 1,
      title: String(formData.get('title') || '新资源'),
      description: String(formData.get('description') || ''),
      detail: String(formData.get('detail') || ''),
      category1: String(formData.get('category1') || '1'),
      category2: String(formData.get('category2') || '11'),
      type: String(formData.get('type') || '文档'),
      author: db.user.nickname,
      downloads: 0,
      score: 0,
      ratingCount: 0,
      userRating: 0,
      date: new Date().toISOString().slice(0, 10),
      tags: String(formData.get('tags') || '').split(',').filter(Boolean),
      fileName: attachments[0]?.name || 'uploaded-file.zip',
      fileSize: attachments[0]?.size || '待处理',
      attachments,
      liked: false,
      favorited: false,
    };
    db.resources.unshift(resource);
    return HttpResponse.json(resource, { status: 201 });
  }),

  http.post('/api/resources/:id/:action', async ({ params, request }) => {
    const resource = db.resources.find((item) => item.id === Number(params.id));
    if (!resource) return HttpResponse.json({ message: '资源不存在' }, { status: 404 });
    const body = (await request.json().catch(() => ({}))) as { attachmentId?: number };

    if (params.action === 'like') resource.liked = !resource.liked;
    if (params.action === 'favorite') resource.favorited = !resource.favorited;
    if (params.action === 'download') {
      resource.downloads += 1;
      const attachment = resource.attachments.find((item) => item.id === body.attachmentId);
      if (attachment) attachment.downloads += 1;
    }

    return HttpResponse.json(resource);
  }),

  http.post('/api/resources/:id/rating', async ({ params, request }) => {
    const resource = db.resources.find((item) => item.id === Number(params.id));
    if (!resource) return HttpResponse.json({ message: '资源不存在' }, { status: 404 });

    const body = (await request.json()) as { score: number };
    const score = Math.max(0, Math.min(5, Number(body.score) || 0));
    const previousRating = resource.userRating || 0;
    const nextCount =
      score === 0 && previousRating > 0
        ? Math.max(0, resource.ratingCount - 1)
        : previousRating > 0 || score === 0
          ? resource.ratingCount
          : resource.ratingCount + 1;
    const totalScore = Math.max(0, resource.score * resource.ratingCount - previousRating + score);

    resource.userRating = score;
    resource.ratingCount = nextCount;
    resource.score = nextCount ? Number((totalScore / nextCount).toFixed(1)) : score;

    return HttpResponse.json(resource);
  }),

  http.post('/api/resources/:id/comments', async ({ request }) => {
    const body = (await request.json()) as { content: string };
    const comment = {
      id: Date.now(),
      author: db.user.nickname,
      content: body.content,
      date: new Date().toISOString().slice(0, 10),
      mine: true,
    };
    db.resourceComments.unshift(comment);
    return HttpResponse.json(comment, { status: 201 });
  }),

  http.get('/api/demands', ({ request }) => {
    const params = paramsFromUrl(request);
    return HttpResponse.json(paginate(filterDemands(db.demands, params), params.page, params.pageSize));
  }),

  http.get('/api/demands/:id', ({ params }) => {
    const demand = db.demands.find((item) => item.id === Number(params.id));
    if (!demand) return HttpResponse.json({ message: '求资源不存在' }, { status: 404 });
    return HttpResponse.json({ demand, comments: db.demandComments });
  }),

  http.post('/api/demands', async ({ request }) => {
    const body = (await request.json()) as Partial<typeof db.demands[number]> & { rewardPoints?: number; tags?: string[] };
    const requestedPoints = Number(body.rewardPoints ?? body.points ?? 0) || 0;
    const rewardType = String(body.rewardType || '').toUpperCase() === 'POINT' || requestedPoints > 0 ? 'POINT' as const : 'FREE' as const;
    const rewardPoints = rewardType === 'POINT' ? requestedPoints : 0;
    const availablePoints = Number(db.user.availablePoints ?? Math.max(0, db.user.points - db.user.frozenPoints));
    if (rewardPoints > availablePoints || rewardPoints > db.user.rewardLimit) {
      return HttpResponse.json({ message: '悬赏积分不能超过可用积分或会员等级上限' }, { status: 400 });
    }
    if (rewardPoints > 0) {
      db.user.frozenPoints += rewardPoints;
      db.user.availablePoints = Math.max(0, db.user.points - db.user.frozenPoints);
    }
    const demand = {
      id: db.demands.length + 1,
      title: body.title || '新求资源',
      description: body.description || '',
      category1: body.category1 || '1',
      category2: body.category2 || '11',
      rewardType,
      points: rewardPoints,
      replyCount: 0,
      author: db.user.nickname,
      date: new Date().toISOString().slice(0, 10),
      status: 'active' as const,
      tags: body.tags || [],
      format: body.format || '不限',
    };
    db.demands.unshift(demand);
    return HttpResponse.json(demand, { status: 201 });
  }),

  http.post('/api/demands/:id/comments', async ({ request, params }) => {
    const demand = db.demands.find((item) => item.id === Number(params.id));
    const body = (await request.json()) as { content: string };
    if (demand) demand.replyCount += 1;
    const comment = {
      id: Date.now(),
      author: db.user.nickname,
      content: body.content,
      date: new Date().toISOString().slice(0, 10),
      mine: true,
    };
    db.demandComments.unshift(comment);
    return HttpResponse.json(comment, { status: 201 });
  }),

  http.post('/api/reports', () => HttpResponse.json({ ok: true })),
];
