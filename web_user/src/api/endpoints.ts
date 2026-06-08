import { apiClient } from './client';
import type { Comment, Demand, ListParams, PagedResult, ProfileSummary, ReportTarget, Resource, User } from '../types';

export async function login(values: { account: string; password: string }) {
  const { data } = await apiClient.post<{ token: string; user: User }>('/auth/login', values);
  return data;
}

export async function register(values: { username: string; email: string; password: string }) {
  const { data } = await apiClient.post<{ token: string; user: User }>('/auth/register', values);
  return data;
}

export async function resetPassword(values: { email: string; code: string; password: string }) {
  const { data } = await apiClient.post<{ ok: boolean }>('/auth/reset-password', values);
  return data;
}

export async function getMe() {
  const { data } = await apiClient.get<User>('/me');
  return data;
}

export async function updateMe(values: Partial<User>) {
  const { data } = await apiClient.put<User>('/me', values);
  return data;
}

export async function changePassword(values: { oldPassword: string; newPassword: string }) {
  const { data } = await apiClient.post<{ ok: boolean; passwordUpdatedAt: string }>('/me/password', values);
  return data;
}

export async function bindEmail(values: { email: string; code: string }) {
  const { data } = await apiClient.post<User>('/me/email', values);
  return data;
}

export async function getProfileSummary() {
  const { data } = await apiClient.get<Partial<ProfileSummary> & { profile?: User }>('/me/summary');
  return normalizeProfileSummary(data);
}

export async function getResources(params: ListParams) {
  const { data } = await apiClient.get<PagedResult<Resource>>('/resources', { params });
  return normalizePage(data, normalizeResource);
}

export async function getResource(id: string | number) {
  const { data } = await apiClient.get<{ resource: Resource; comments: Comment[] }>(`/resources/${id}`);
  return {
    resource: normalizeResource(data.resource),
    comments: data.comments || [],
  };
}

export async function publishResource(formData: FormData) {
  const { data } = await apiClient.post<Resource>('/resources', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return normalizeResource(data);
}

export async function toggleResourceAction(id: number, action: 'like' | 'favorite' | 'download', attachmentId?: number) {
  const { data } = await apiClient.post<Resource>(`/resources/${id}/${action}`, { attachmentId });
  return normalizeResource(data);
}

export async function rateResource(id: number, score: number) {
  const { data } = await apiClient.post<Resource>(`/resources/${id}/rating`, { score });
  return normalizeResource(data);
}

export async function getDemands(params: ListParams) {
  const { data } = await apiClient.get<PagedResult<Demand>>('/demands', { params });
  return normalizePage(data, normalizeDemand);
}

export async function getDemand(id: string | number) {
  const { data } = await apiClient.get<{ demand?: Demand; request?: Demand; comments?: Comment[]; replies?: Comment[]; answers?: Comment[] }>(`/demands/${id}`);
  const replies = data.replies || data.answers || [];
  return {
    demand: normalizeDemand(data.demand || data.request),
    comments: [...replies, ...(data.comments || [])].map(normalizeComment),
  };
}

export async function publishDemand(values: Record<string, unknown>) {
  const { data } = await apiClient.post<Demand>('/demands', normalizeDemandPayload(values));
  return normalizeDemand(data);
}

export async function addComment(kind: 'resources' | 'demands', id: number, content: string) {
  const url = kind === 'demands' ? `/demands/${id}/replies` : `/${kind}/${id}/comments`;
  const { data } = await apiClient.post<Comment>(url, { content });
  return normalizeComment(data);
}

export async function reportContent(values: { target: ReportTarget; targetId: number; type: string; reason: string }) {
  const { data } = await apiClient.post<{ ok: boolean }>('/reports', values);
  return data;
}

function normalizePage<T>(page: PagedResult<T>, mapItem: (item: T) => T): PagedResult<T> {
  return {
    ...page,
    items: (page.items || []).map(mapItem),
    pageSize: page.pageSize || 10,
  };
}

function normalizeResource(resource: Resource): Resource {
  return {
    ...resource,
    type: normalizeResourceType(resource?.type || (resource as Resource & { resourceType?: string })?.resourceType),
    tags: resource?.tags || [],
    attachments: resource?.attachments || [],
    downloads: Number(resource?.downloads || 0),
    score: Number(resource?.score || 0),
    ratingCount: Number(resource?.ratingCount || 0),
    userRating: Number(resource?.userRating || 0),
    liked: Boolean(resource?.liked),
    favorited: Boolean(resource?.favorited),
  };
}

function normalizeResourceType(type?: string): string {
  const normalized = String(type || '').trim();
  const map: Record<string, string> = {
    DOCUMENT: '文档',
    document: '文档',
    SOFTWARE: '软件',
    software: '软件',
    SOURCE_CODE: '源码',
    source: '源码',
    MATERIAL: '素材',
    material: '素材',
    COURSE: '教程',
    course: '教程',
    TEMPLATE: '模板',
    template: '模板',
    LINK: '链接',
    link: '链接',
  };
  return map[normalized] || normalized || '文档';
}

function normalizeDemand(demand?: Demand): Demand {
  const rawStatus = String(demand?.status || '');
  const status = rawStatus === 'RESOLVED' || rawStatus === 'solved' ? 'solved' : 'active';
  const points = Number(demand?.points || (demand as Demand & { rewardPoints?: number })?.rewardPoints || 0);
  const rewardType: Demand['rewardType'] = String(demand?.rewardType || '').toUpperCase() === 'POINT' || points > 0 ? 'POINT' : 'FREE';
  return {
    ...demand,
    id: demand?.id || 0,
    title: demand?.title || '',
    description: demand?.description || '',
    category1: demand?.category1 || '',
    category2: demand?.category2 || '',
    rewardType,
    points,
    replyCount: Number(demand?.replyCount || 0),
    author: demand?.author || '',
    date: demand?.date || '',
    status,
    tags: demand?.tags || [],
    format: demand?.format || '',
  };
}

function normalizeComment(comment?: Comment): Comment {
  return {
    ...(comment || {}),
    id: comment?.id || 0,
    author: comment?.author || '',
    content: comment?.content || '',
    date: comment?.date || '',
    mine: Boolean(comment?.mine),
    accepted: Boolean(comment?.accepted),
    replies: (comment?.replies || []).map(normalizeComment),
  };
}

function normalizeDemandPayload(values: Record<string, unknown>) {
  const requestedPoints = Number(values.rewardPoints ?? values.points ?? 0) || 0;
  const rawRewardType = String(values.rewardType || '').toUpperCase();
  const rewardType = rawRewardType === 'POINT' || (!rawRewardType && requestedPoints > 0) ? 'POINT' : 'FREE';
  const rewardPoints = rewardType === 'POINT' ? requestedPoints : 0;
  return {
    ...values,
    tags: Array.isArray(values.tags) ? values.tags.join(',') : values.tags,
    content: values.content || values.description,
    rewardType,
    rewardPoints,
    points: rewardPoints,
    expectedFormat: values.expectedFormat || values.format,
  };
}

function normalizeProfileSummary(data: Partial<ProfileSummary> & { profile?: User }): ProfileSummary {
  return {
    resources: (data.resources || []).map(normalizeResource),
    demands: (data.demands || []).map(normalizeDemand),
    favorites: (data.favorites || []).map(normalizeResource),
    likes: (data.likes || []).map(normalizeResource),
    messages: data.messages || [],
    loginLogs: data.loginLogs || [],
  };
}
