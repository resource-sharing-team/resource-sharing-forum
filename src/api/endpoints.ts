import { apiClient, apiHostURL, profileBasePath } from './client';
import {
  mapAnnouncements,
  mapCategories,
  mapComment,
  mapDemand,
  mapDemandDetail,
  mapDownloadInfo,
  mapLoginLogs,
  mapNotifications,
  mapPaged,
  mapProfileSummary,
  mapResource,
  mapResourceDetail,
  mapResourceTypes,
  mapUser,
} from './adapters';
import type {
  Announcement,
  Category,
  Comment,
  Demand,
  DownloadInfo,
  ListParams,
  LoginLog,
  NotificationMessage,
  PagedResult,
  ProfileSummary,
  ReportTarget,
  Resource,
  ResourceTypeOption,
  User,
} from '../types';

type AuthPayload = {
  token?: string;
  user?: unknown;
};

export async function login(values: { account: string; password: string }) {
  const { data } = await apiClient.post<AuthPayload>('/auth/login', { ...values, rememberMe: true });
  return {
    token: data.token || '',
    user: mapUser(data.user),
  };
}

export async function register(values: { username: string; email: string; password: string }) {
  const { data } = await apiClient.post<AuthPayload>('/auth/register', values);
  return {
    token: data.token || '',
    user: mapUser(data.user),
  };
}

export async function resetPassword(values: { email: string; code: string; password: string }) {
  const payload = {
    account: values.email,
    email: values.email,
    code: values.code,
    password: values.password,
    newPassword: values.password,
  };
  const { data } = await apiClient.post<{ ok?: boolean }>('/auth/reset-password', payload);
  return { ok: data?.ok !== false };
}

export async function sendResetPasswordCode(values: { email: string }) {
  const { data } = await apiClient.post<{ ok?: boolean; devCode?: string }>('/auth/reset-password/code', {
    account: values.email,
    email: values.email,
  });
  return { ok: data?.ok !== false, devCode: data?.devCode };
}

export async function getMe(): Promise<User> {
  const { data } = await apiClient.get<unknown>(profileBasePath);
  return mapUser(data);
}

export async function updateMe(values: Partial<User>): Promise<User> {
  const { data } = await apiClient.put<unknown>(profileBasePath, values);
  return mapUser(data);
}

export async function changePassword(values: { oldPassword: string; newPassword: string }) {
  const payload = {
    ...values,
    currentPassword: values.oldPassword,
    password: values.newPassword,
  };
  const { data } = await apiClient.post<{ ok?: boolean; passwordUpdatedAt?: string }>(`${profileBasePath}/password`, payload);
  return {
    ok: data?.ok !== false,
    passwordUpdatedAt: data?.passwordUpdatedAt || new Date().toISOString().slice(0, 10),
  };
}

export async function bindEmail(values: { email: string }): Promise<User> {
  const { data } = await apiClient.post<unknown>(`${profileBasePath}/email`, {
    ...values,
    newEmail: values.email,
  });
  return mapUser(data);
}

export async function getProfileSummary(): Promise<ProfileSummary> {
  const { data } = await apiClient.get<unknown>(`${profileBasePath}/summary`);
  return mapProfileSummary(data);
}

export async function getResources(params: ListParams): Promise<PagedResult<Resource>> {
  const { data } = await apiClient.get<unknown>('/resources', { params: toBackendListParams(params) });
  return mapPaged(data, mapResource);
}

export async function getResource(id: string | number): Promise<{ resource: Resource; comments: Comment[] }> {
  const { data } = await apiClient.get<unknown>(`/resources/${id}`);
  return mapResourceDetail(data);
}

export async function publishResource(formData: FormData): Promise<Resource> {
  const { data } = await apiClient.post<unknown>('/resources', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return mapResource(data);
}

export async function toggleResourceAction(id: number, action: 'like' | 'favorite' | 'download', attachmentId?: number): Promise<Resource> {
  if (action === 'download') {
    await apiClient.post(`/resources/${id}/download`, { attachmentId });
    return (await getResource(id)).resource;
  }

  const { data } = await apiClient.post<unknown>(`/resources/${id}/${action}`);
  return mapResource(data);
}

export async function downloadAttachment(attachmentId: number): Promise<DownloadInfo> {
  const { data } = await apiClient.get<unknown>(`/attachments/${attachmentId}/download`);
  return mapDownloadInfo(data);
}

export async function rateResource(id: number, score: number): Promise<Resource> {
  if (score <= 0) {
    return (await getResource(id)).resource;
  }

  const { data } = await apiClient.post<unknown>(`/resources/${id}/rating`, { score: Math.min(5, score) });
  return mapResource(data);
}

export async function getDemands(params: ListParams): Promise<PagedResult<Demand>> {
  const { data } = await apiClient.get<unknown>('/requests', { params: toBackendListParams(params) });
  return mapPaged(data, mapDemand);
}

export async function getDemand(id: string | number): Promise<{ demand: Demand; comments: Comment[] }> {
  const { data } = await apiClient.get<unknown>(`/requests/${id}`);
  return mapDemandDetail(data);
}

export async function publishDemand(values: Record<string, unknown>): Promise<Demand> {
  const payload = {
    ...values,
    categoryId: values.category2,
    expectedFormat: values.format,
    rewardPoints: values.points,
  };
  const { data } = await apiClient.post<unknown>('/requests', payload);
  return mapDemand(data);
}

export async function cancelDemand(id: number) {
  const { data } = await apiClient.post<{ ok?: boolean }>(`/requests/${id}/cancel`);
  return { ok: data?.ok !== false };
}

export async function addComment(kind: 'resources' | 'demands', id: number, values: { content: string; parentId?: number }): Promise<Comment> {
  if (kind === 'demands' && !values.parentId) {
    const { data } = await apiClient.post<unknown>(`/requests/${id}/replies`, { content: values.content });
    return mapComment(data);
  }

  const { data } = await apiClient.post<unknown>('/comments', {
    targetType: kind === 'resources' ? 'RESOURCE' : 'REQUEST_POST',
    targetId: id,
    content: values.content,
    parentId: values.parentId,
  });
  return mapComment(data);
}

export async function deleteComment(id: number) {
  const { data } = await apiClient.delete<{ ok?: boolean }>(`/comments/${id}`);
  return { ok: data?.ok !== false };
}

export async function reportContent(values: { target: ReportTarget; targetId: number; type: string; reason: string }) {
  const url = values.target === 'COPYRIGHT' || values.type === 'COPYRIGHT' ? '/reports/copyright-complaints' : '/reports';
  const { data } = await apiClient.post<{ ok?: boolean }>(url, {
    ...values,
    targetType: values.target === 'DEMAND' ? 'REQUEST_POST' : values.target,
  });
  return { ok: data?.ok !== false };
}

export async function getNotificationMessages(params: { page?: number; pageSize?: number } = {}): Promise<PagedResult<NotificationMessage>> {
  const { data } = await apiClient.get<unknown>('/notifications', { params: { page: params.page || 1, size: params.pageSize || 20 } });
  return mapNotifications(data);
}

export async function markNotificationRead(id: number) {
  await apiClient.post(`/notifications/${id}/read`);
  return { ok: true };
}

export async function markAllNotificationsRead() {
  await apiClient.post('/notifications/read-all');
  return { ok: true };
}

export async function getCategories(): Promise<Category[]> {
  const { data } = await apiClient.get<unknown>('/categories');
  return mapCategories(data);
}

export async function getResourceTypes(): Promise<ResourceTypeOption[]> {
  const { data } = await apiClient.get<unknown>('/resource-types');
  return mapResourceTypes(data);
}

export async function getAnnouncements(params: { page?: number; pageSize?: number } = {}): Promise<PagedResult<Announcement>> {
  const { data } = await apiClient.get<unknown>('/announcements', { params: { page: params.page || 1, size: params.pageSize || 5 } });
  return mapAnnouncements(data);
}

export async function getUserResources(params: ListParams = {}): Promise<PagedResult<Resource>> {
  const { data } = await apiClient.get<unknown>('/user/resources', { params: toBackendListParams(params) });
  return mapPaged(data, mapResource);
}

export async function getUserRequests(params: ListParams = {}): Promise<PagedResult<Demand>> {
  const { data } = await apiClient.get<unknown>('/user/requests', { params: toBackendListParams(params) });
  return mapPaged(data, mapDemand);
}

export async function getUserFavorites(params: ListParams = {}): Promise<PagedResult<Resource>> {
  const { data } = await apiClient.get<unknown>('/user/favorites', { params: toBackendListParams(params) });
  return mapPaged(data, mapResource);
}

export async function getUserLikes(params: ListParams = {}): Promise<PagedResult<Resource>> {
  const { data } = await apiClient.get<unknown>('/user/likes', { params: toBackendListParams(params) });
  return mapPaged(data, mapResource);
}

export async function getUserLoginRecords(params: { page?: number; pageSize?: number } = {}): Promise<PagedResult<LoginLog>> {
  const { data } = await apiClient.get<unknown>('/user/login-records', { params: { page: params.page || 1, size: params.pageSize || 20 } });
  return mapLoginLogs(data);
}

export function absoluteDownloadUrl(downloadUrl: string) {
  return new URL(downloadUrl, apiHostURL).toString();
}

function toBackendListParams(params: ListParams) {
  const categoryId = params.cate2 || params.cate1;
  return {
    page: params.page,
    size: params.pageSize,
    keyword: params.keyword,
    categoryId,
    category2: params.cate2,
    resourceType: toResourceType(params.type),
    type: params.type,
    status: toRequestStatus(params.status),
    sort: params.sort,
  };
}

function toRequestStatus(status?: string) {
  if (status === 'active') return 'ONGOING';
  if (status === 'solved') return 'RESOLVED';
  return status;
}

function toResourceType(type?: string) {
  const map: Record<string, string> = {
    文档: 'DOCUMENT',
    软件: 'SOFTWARE',
    源码: 'SOURCE_CODE',
    素材: 'MATERIAL',
    教程: 'COURSE',
    模板: 'TEMPLATE',
    链接: 'LINK',
  };
  return type ? map[type] || type : undefined;
}
