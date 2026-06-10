import { http, HttpResponse } from 'msw';
import { db } from './db';
import { filterDemands, filterResources, paginate } from '../utils/listing';
import type { Comment, ListParams, ResourceAttachment } from '../types';

function paramsFromUrl(request: Request): ListParams {
  const url = new URL(request.url);
  const params = Object.fromEntries(url.searchParams.entries());
  return {
    ...params,
    page: params.page ? Number(params.page) : 1,
    pageSize: params.pageSize ? Number(params.pageSize) : params.size ? Number(params.size) : 10,
  };
}

function uploadedAttachments(formData: FormData): ResourceAttachment[] {
  const files = formData.getAll('files');
  if (!files.length) {
    return [
      {
        id: 1,
        name: String(formData.get('fileName') || 'uploaded-file.zip'),
        size: '\u5f85\u5904\u7406',
        type: '\u6587\u4ef6',
        downloads: 0,
      },
    ];
  }

  return files.map((file, index) => {
    const fileName = file instanceof File ? file.name : `attachment-${index + 1}`;
    return {
      id: index + 1,
      name: fileName,
      size: '\u5f85\u5904\u7406',
      type: fileName.includes('.') ? fileName.split('.').pop()?.toUpperCase() || '\u6587\u4ef6' : '\u6587\u4ef6',
      downloads: 0,
    };
  });
}

function apiGet(path: string, resolver: Parameters<typeof http.get>[1]) {
  return [http.get(`/api${path}`, resolver), http.get(`/api/v1${path}`, resolver)];
}

function apiPost(path: string, resolver: Parameters<typeof http.post>[1]) {
  return [http.post(`/api${path}`, resolver), http.post(`/api/v1${path}`, resolver)];
}

function apiPut(path: string, resolver: Parameters<typeof http.put>[1]) {
  return [http.put(`/api${path}`, resolver), http.put(`/api/v1${path}`, resolver)];
}

function apiDelete(path: string, resolver: Parameters<typeof http.delete>[1]) {
  return [http.delete(`/api${path}`, resolver), http.delete(`/api/v1${path}`, resolver)];
}

function createComment(content: string, extra: Partial<Comment> = {}): Comment {
  return {
    id: Date.now() + Math.floor(Math.random() * 1000),
    author: db.user.nickname,
    content,
    date: new Date().toISOString().slice(0, 10),
    mine: true,
    ...extra,
  };
}

function profileSummary() {
  const favorites = db.resources.filter((item) => item.favorited);
  const likes = db.resources.filter((item) => item.liked);
  return {
    ...db.profileSummary,
    favorites,
    likes,
    resources: db.resources.slice(0, 3),
    demands: db.demands.slice(0, 3),
  };
}

export const handlers = [
  ...apiPost('/auth/login', async ({ request }) => {
    const body = (await request.json()) as { account?: string };
    return HttpResponse.json({
      token: 'mock-user-token',
      user: { ...db.user, username: body.account || db.user.username },
    });
  }),

  ...apiPost('/auth/register', async ({ request }) => {
    const body = (await request.json()) as { username: string; email: string };
    db.user = { ...db.user, username: body.username, nickname: body.username, email: body.email, emailVerified: true };
    return HttpResponse.json({ token: 'mock-user-token', user: db.user }, { status: 201 });
  }),

  ...apiPost('/auth/reset-password', () => HttpResponse.json({ ok: true })),

  ...apiGet('/me', () => HttpResponse.json(db.user)),
  ...apiGet('/user/profile', () => HttpResponse.json(db.user)),

  ...apiPut('/me', async ({ request }) => {
    const body = (await request.json()) as Partial<typeof db.user>;
    db.user = { ...db.user, ...body };
    return HttpResponse.json(db.user);
  }),
  ...apiPut('/user/profile', async ({ request }) => {
    const body = (await request.json()) as Partial<typeof db.user>;
    db.user = { ...db.user, ...body };
    return HttpResponse.json(db.user);
  }),

  ...apiPost('/me/password', () => {
    db.user.passwordUpdatedAt = new Date().toISOString().slice(0, 10);
    return HttpResponse.json({ ok: true, passwordUpdatedAt: db.user.passwordUpdatedAt });
  }),
  ...apiPost('/user/profile/password', () => {
    db.user.passwordUpdatedAt = new Date().toISOString().slice(0, 10);
    return HttpResponse.json({ ok: true, passwordUpdatedAt: db.user.passwordUpdatedAt });
  }),

  ...apiPost('/me/email', async ({ request }) => {
    const body = (await request.json()) as { email: string };
    db.user = { ...db.user, email: body.email, emailVerified: true };
    return HttpResponse.json(db.user);
  }),
  ...apiPost('/user/profile/email', async ({ request }) => {
    const body = (await request.json()) as { email: string };
    db.user = { ...db.user, email: body.email, emailVerified: true };
    return HttpResponse.json(db.user);
  }),

  ...apiGet('/me/summary', () => HttpResponse.json(profileSummary())),
  ...apiGet('/user/profile/summary', () => HttpResponse.json(profileSummary())),
  ...apiGet('/notifications', () => HttpResponse.json(paginate(db.profileSummary.messages, 1, 20))),
  ...apiGet('/notifications/unread-count', () =>
    HttpResponse.json({ count: db.profileSummary.messages.filter((item) => item.unread).length }),
  ),

  ...apiGet('/resources', ({ request }) => {
    const params = paramsFromUrl(request);
    return HttpResponse.json(paginate(filterResources(db.resources, params), params.page, params.pageSize));
  }),

  ...apiGet('/resources/:id', ({ params }) => {
    const resource = db.resources.find((item) => item.id === Number(params.id));
    if (!resource) return HttpResponse.json({ message: 'resource not found' }, { status: 404 });
    return HttpResponse.json({ resource, comments: db.resourceComments });
  }),

  ...apiPost('/resources', async ({ request }) => {
    const formData = await request.formData();
    const attachments = uploadedAttachments(formData);
    const resource = {
      id: db.resources.length + 1,
      title: String(formData.get('title') || '\u65b0\u8d44\u6e90'),
      description: String(formData.get('description') || ''),
      detail: String(formData.get('detail') || ''),
      category1: String(formData.get('category1') || '1'),
      category2: String(formData.get('category2') || '11'),
      type: String(formData.get('type') || '\u6587\u6863'),
      author: db.user.nickname,
      downloads: 0,
      score: 0,
      ratingCount: 0,
      userRating: 0,
      date: new Date().toISOString().slice(0, 10),
      tags: String(formData.get('tags') || '')
        .split(/[,，]/)
        .filter(Boolean),
      fileName: attachments[0]?.name || 'uploaded-file.zip',
      fileSize: attachments[0]?.size || '\u5f85\u5904\u7406',
      attachments,
      liked: false,
      favorited: false,
    };
    db.resources.unshift(resource);
    return HttpResponse.json(resource, { status: 201 });
  }),

  ...apiPost('/resources/:id/:action', async ({ params, request }) => {
    const resource = db.resources.find((item) => item.id === Number(params.id));
    if (!resource) return HttpResponse.json({ message: 'resource not found' }, { status: 404 });
    const body = (await request.json().catch(() => ({}))) as { attachmentId?: number };

    if (params.action === 'like') resource.liked = !resource.liked;
    if (params.action === 'favorite') resource.favorited = !resource.favorited;
    if (params.action === 'rating') {
      const score = Math.max(0, Math.min(5, Number((body as { score?: number }).score) || 0));
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
    }
    if (params.action === 'download') {
      resource.downloads += 1;
      const attachment = resource.attachments.find((item) => item.id === body.attachmentId);
      if (attachment) attachment.downloads += 1;
      return HttpResponse.json({
        recordId: Date.now(),
        fileName: attachment?.name || resource.fileName,
        downloadUrl: `/api/v1/attachments/${body.attachmentId || resource.attachments[0]?.id || resource.id}/download`,
      });
    }

    return HttpResponse.json(resource);
  }),

  ...apiPost('/resources/:id/rating', async ({ params, request }) => {
    const resource = db.resources.find((item) => item.id === Number(params.id));
    if (!resource) return HttpResponse.json({ message: 'resource not found' }, { status: 404 });

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

  ...apiPost('/resources/:id/comments', async ({ request }) => {
    const body = (await request.json()) as { content: string };
    const comment = createComment(body.content);
    db.resourceComments.unshift(comment);
    return HttpResponse.json(comment, { status: 201 });
  }),

  ...apiPost('/resources/:id/comments/:commentId/replies', async ({ request, params }) => {
    const parent = db.resourceComments.find((item) => item.id === Number(params.commentId));
    if (!parent) return HttpResponse.json({ message: 'comment not found' }, { status: 404 });

    const body = (await request.json()) as { content: string };
    const reply = createComment(body.content, { replyToAuthor: parent.author });
    parent.replies = [...(parent.replies || []), reply];
    return HttpResponse.json(reply, { status: 201 });
  }),

  ...apiGet('/requests', ({ request }) => {
    const params = paramsFromUrl(request);
    return HttpResponse.json(paginate(filterDemands(db.demands, params), params.page, params.pageSize));
  }),

  ...apiGet('/requests/:id', ({ params }) => {
    const demand = db.demands.find((item) => item.id === Number(params.id));
    if (!demand) return HttpResponse.json({ message: 'request not found' }, { status: 404 });
    return HttpResponse.json({ request: demand, replies: db.demandComments, comments: [] });
  }),

  ...apiPost('/requests', async ({ request }) => {
    const body = (await request.json()) as Partial<(typeof db.demands)[number]> & { rewardPoints?: number; expectedFormat?: string };
    const demand = {
      id: db.demands.length + 1,
      title: body.title || '\u65b0\u6c42\u8d44\u6e90',
      description: body.description || '',
      category1: body.category1 || '1',
      category2: body.category2 || '11',
      points: Number(body.points || body.rewardPoints || 0),
      replyCount: 0,
      author: db.user.nickname,
      date: new Date().toISOString().slice(0, 10),
      status: 'active' as const,
      tags: Array.isArray(body.tags) ? body.tags : String(body.tags || '').split(/[,，]/).filter(Boolean),
      format: body.format || body.expectedFormat || '\u4e0d\u9650',
    };
    db.demands.unshift(demand);
    return HttpResponse.json(demand, { status: 201 });
  }),

  ...apiDelete('/requests/:id', ({ params }) => {
    const id = Number(params.id);
    const before = db.demands.length;
    db.demands = db.demands.filter((item) => item.id !== id);
    if (db.demands.length === before) return HttpResponse.json({ message: 'request not found' }, { status: 404 });
    return HttpResponse.json({ ok: true });
  }),

  ...apiPost('/requests/:id/replies', async ({ request, params }) => {
    const demand = db.demands.find((item) => item.id === Number(params.id));
    const body = (await request.json()) as { content: string };
    if (demand) demand.replyCount += 1;
    const comment = createComment(body.content);
    db.demandComments.unshift(comment);
    return HttpResponse.json(comment, { status: 201 });
  }),

  ...apiPost('/requests/:id/replies/:replyId/replies', async ({ request, params }) => {
    const demand = db.demands.find((item) => item.id === Number(params.id));
    const parent = db.demandComments.find((item) => item.id === Number(params.replyId));
    if (!parent) return HttpResponse.json({ message: 'reply not found' }, { status: 404 });

    const body = (await request.json()) as { content: string };
    if (demand) demand.replyCount += 1;
    const reply = createComment(body.content, { replyToAuthor: parent.author });
    parent.replies = [...(parent.replies || []), reply];
    return HttpResponse.json(reply, { status: 201 });
  }),

  ...apiPost('/reports', () => HttpResponse.json({ ok: true })),
];
