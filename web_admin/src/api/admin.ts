import {
  ADMIN_AUTH_KEY,
  LEGACY_ADMIN_SESSION_KEY,
  apiClient,
  clearStoredAdminAuth,
} from './client';
import {
  categories,
  comments,
  complaints,
  logs,
  managedResources,
  memberLevels,
  pendingResources,
  reports,
  requestPosts,
  users,
} from '../data/mockAdmin';
import type {
  AdminCatalogData,
  AdminCategory,
  AdminCatalogOptions,
  AdminCatalogSection,
  AdminAppeal,
  AdminComment,
  AdminComplaint,
  AdminComplianceData,
  AdminComplianceSection,
  AdminConfigData,
  AdminContentData,
  AdminContentSection,
  AdminLog,
  AdminReport,
  AdminReportAction,
  AdminRequestPost,
  AdminResource,
  AdminSession,
  AdminUser,
  ConfigItem,
  MemberLevel,
  PageResult,
  RawId,
} from '../types';

const ENABLE_MOCKS = import.meta.env.VITE_ENABLE_MOCKS === 'true';
export { ADMIN_AUTH_EXPIRED_EVENT, ADMIN_AUTH_KEY, LEGACY_ADMIN_SESSION_KEY } from './client';

type LoginPayload = {
  token: string;
  role?: string;
  expireAt?: string;
  user?: Record<string, unknown>;
};

type CatalogCreatePayload = {
  name: string;
  level?: 1 | 2;
  parentId?: RawId;
  sortOrder?: number;
};

type SectionPage<T> = PageResult<T> & {
  section?: string;
};

const mockContent: AdminContentData = {
  auditRows: clone(pendingResources),
  resourceRows: clone(managedResources),
  requestRows: clone(requestPosts),
  commentRows: clone(comments),
};
const mockUsers = clone(users);
const mockCompliance: AdminComplianceData = {
  reports: clone(reports),
  complaints: clone(complaints),
  appeals: [
    {
      id: 'S001',
      rawId: 'S001',
      targetId: 'R005',
      targetType: 'RESOURCE',
      appellant: '肆意妄为',
      reason: '资源已整改，请求恢复',
      status: '待审核',
      rawStatus: 'PENDING',
    },
  ],
};
const mockCatalog: AdminCatalogData = {
  categories: clone(categories.filter((item) => item.type !== '标签')).map((item) => ({ ...item, kind: 'CATEGORY' })),
  tags: clone(categories.filter((item) => item.type === '标签')).map((item) => ({ ...item, kind: 'TAG' })),
  rows: clone(categories).map((item) => ({ ...item, kind: item.type === '标签' ? 'TAG' : 'CATEGORY' })),
};
const mockConfig: AdminConfigData = {
  memberLevels: clone(memberLevels).map((item, index) => ({ ...item, rawId: index + 1, id: String(index + 1) })),
  scoreRules: [
    { key: 'point.daily_login', label: '每日登录 + 积分', value: '10', valueType: 'INTEGER' },
    { key: 'point.resource_favorited', label: '资源被收藏 + 积分', value: '5', valueType: 'INTEGER' },
    { key: 'point.resource_liked', label: '资源被点赞 + 积分', value: '3', valueType: 'INTEGER' },
    { key: 'point.resource_approved', label: '资源审核通过 + 积分', value: '10', valueType: 'INTEGER' },
    { key: 'point.resource_downloaded', label: '资源被下载 + 积分/次', value: '5', valueType: 'INTEGER' },
    { key: 'point.request_accepted', label: '求资源回答被采纳 + 奖励积分', value: '10', valueType: 'INTEGER' },
    { key: 'point.violation_penalty', label: '违规确认 - 扣积分', value: '10', valueType: 'INTEGER' },
    { key: 'point.resource_offline_penalty', label: '资源违规下架 - 扣积分', value: '20', valueType: 'INTEGER' },
    { key: 'point.comment_delete_penalty', label: '评论违规删除 - 扣积分', value: '5', valueType: 'INTEGER' },
  ],
  systemParams: [
    { key: 'upload.allowed_types', label: '允许上传文件类型', value: 'pdf,doc,docx,ppt,pptx,xls,xlsx,zip,rar,7z,png,jpg,jpeg,txt,md' },
    { key: 'resource.daily_publish_limit', label: '用户每日最大发布资源数', value: '5', valueType: 'INTEGER' },
    { key: 'request.daily_publish_limit', label: '用户每日最大发布求资源数', value: '5', valueType: 'INTEGER' },
    { key: 'auth.email_code_minutes', label: '邮箱验证码有效期（分钟）', value: '10', valueType: 'INTEGER' },
    { key: 'auth.login_fail_lock_count', label: '连续登录失败锁定次数', value: '5', valueType: 'INTEGER' },
    { key: 'auth.login_fail_lock_minutes', label: '登录失败锁定时长（分钟）', value: '10', valueType: 'INTEGER' },
  ],
};
const mockLogs = clone(logs);

export function readAdminSession(): AdminSession | null {
  try {
    const persisted = localStorage.getItem(ADMIN_AUTH_KEY);
    const state = persisted ? JSON.parse(persisted).state : null;
    if (!state?.token) return null;
    if (state.expireAt && Date.parse(state.expireAt) <= Date.now()) {
      clearStoredAdminAuth();
      return null;
    }
    return {
      token: state.token,
      role: state.role || state.user?.role || 'ADMIN',
      user: state.user || { id: 2, username: 'admin', role: 'ADMIN' },
      expireAt: state.expireAt,
    };
  } catch {
    return null;
  }
}

export function storeAdminSession(session: AdminSession) {
  localStorage.setItem(
    ADMIN_AUTH_KEY,
    JSON.stringify({
      state: {
        token: session.token,
        user: session.user,
        role: session.role,
        expireAt: session.expireAt,
      },
    }),
  );
  localStorage.removeItem(LEGACY_ADMIN_SESSION_KEY);
}

export function clearAdminSession() {
  clearStoredAdminAuth();
}

export async function loginAdmin(account: string, password: string): Promise<AdminSession> {
  if (ENABLE_MOCKS) {
    if (account.trim() !== 'admin' || password !== 'password') {
      throw new Error('账号或密码错误');
    }
    return {
      token: 'mock-admin-token',
      role: 'ADMIN',
      expireAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
      user: { id: 2, username: 'admin', nickname: '审核管理员', role: 'ADMIN' },
    };
  }

  const response = await apiClient.post<LoginPayload>('/auth/login', { account, password });
  const payload = response.data;
  const role = payload.role || String(payload.user?.role || '');
  if (role !== 'ADMIN') {
    throw new Error('当前账号不是管理员，无法进入后台');
  }
  return {
    token: payload.token,
    role,
    expireAt: payload.expireAt,
    user: {
      id: payload.user?.id as RawId,
      username: String(payload.user?.username || account),
      nickname: String(payload.user?.nickname || payload.user?.username || '管理员'),
      email: payload.user?.email ? String(payload.user.email) : undefined,
      role,
    },
  };
}

export async function getAdminContent(): Promise<AdminContentData> {
  if (ENABLE_MOCKS) return clone(mockContent);
  const response = await apiClient.get<AdminContentData>('/admin/content', { params: { size: 100 } });
  return withContentDefaults(response.data);
}

export async function getAdminContentPage(section: AdminContentSection, params: Record<string, string> = {}): Promise<PageResult<AdminResource | AdminRequestPost | AdminComment>> {
  const pageParams = withDefaultSize(params, 10);
  if (ENABLE_MOCKS) {
    return pageOf(contentSectionRows(section, mockContent), pageParams);
  }
  const response = await apiClient.get<SectionPage<AdminResource | AdminRequestPost | AdminComment> | AdminContentData>('/admin/content', {
    params: { ...pageParams, section },
  });
  if (isPageLike<AdminResource | AdminRequestPost | AdminComment>(response.data)) {
    return normalizePage(response.data, pageParams);
  }
  return pageOf(contentSectionRows(section, withContentDefaults(response.data)), pageParams);
}

export async function getAdminUsers(params: Record<string, string> = {}): Promise<PageResult<AdminUser>> {
  if (ENABLE_MOCKS) {
    const keyword = params.keyword?.trim();
    const status = params.status?.trim();
    const filtered = mockUsers.filter((item) => {
      const matchesKeyword = !keyword || [item.id, item.nickname, item.username, item.email].some((value) => String(value || '').includes(keyword));
      const matchesStatus = !status || item.status === status || item.rawStatus === status;
      return matchesKeyword && matchesStatus;
    });
    return pageOf(filtered, params);
  }
  const response = await apiClient.get<PageResult<AdminUser>>('/admin/users', { params });
  return normalizePage(response.data);
}

export async function getAdminCompliance(): Promise<AdminComplianceData> {
  if (ENABLE_MOCKS) return clone(mockCompliance);
  const response = await apiClient.get<AdminComplianceData>('/admin/compliance', { params: { size: 100 } });
  return {
    reports: response.data.reports || [],
    complaints: response.data.complaints || [],
    appeals: response.data.appeals || [],
  };
}

export async function getAdminCompliancePage(section: AdminComplianceSection, params: Record<string, string> = {}): Promise<PageResult<AdminReport | AdminComplaint | AdminAppeal>> {
  const pageParams = withDefaultSize(params, 10);
  if (ENABLE_MOCKS) {
    return pageOf(complianceSectionRows(section, mockCompliance), pageParams);
  }
  const response = await apiClient.get<SectionPage<AdminReport | AdminComplaint | AdminAppeal> | AdminComplianceData>('/admin/compliance', {
    params: { ...pageParams, section },
  });
  if (isPageLike<AdminReport | AdminComplaint | AdminAppeal>(response.data)) {
    return normalizePage(response.data, pageParams);
  }
  return pageOf(complianceSectionRows(section, withComplianceDefaults(response.data)), pageParams);
}

export async function getAdminCatalog(): Promise<AdminCatalogData> {
  if (ENABLE_MOCKS) return clone(mockCatalog);
  const response = await apiClient.get<AdminCatalogData>('/admin/catalog', { params: { size: 100 } });
  return {
    categories: response.data.categories || [],
    tags: response.data.tags || [],
    rows: response.data.rows || [],
  };
}

export async function getAdminCatalogPage(section: AdminCatalogSection, params: Record<string, string> = {}): Promise<PageResult<AdminCategory>> {
  const pageParams = withDefaultSize(params, 10);
  if (ENABLE_MOCKS) {
    return pageOf(filterCatalogRows(catalogSectionRows(section, mockCatalog), pageParams), pageParams);
  }
  const response = await apiClient.get<SectionPage<AdminCategory> | AdminCatalogData>('/admin/catalog', {
    params: { ...pageParams, section },
  });
  if (isPageLike<AdminCategory>(response.data)) {
    return normalizePage(response.data, pageParams);
  }
  return pageOf(catalogSectionRows(section, withCatalogDefaults(response.data)), pageParams);
}

export async function getAdminCatalogOptions(): Promise<AdminCatalogOptions> {
  if (ENABLE_MOCKS) return mockCatalogOptions();
  const response = await apiClient.get<AdminCatalogOptions>('/admin/catalog/options');
  return withCatalogOptionsDefaults(response.data);
}

export async function getAdminConfigFull(): Promise<AdminConfigData> {
  if (ENABLE_MOCKS) return clone(mockConfig);
  const response = await apiClient.get<AdminConfigData>('/admin/config/full');
  return {
    memberLevels: response.data.memberLevels || [],
    scoreRules: response.data.scoreRules || [],
    systemParams: response.data.systemParams || [],
  };
}

export async function getAdminLogs(params: Record<string, string> = {}): Promise<PageResult<AdminLog>> {
  if (ENABLE_MOCKS) {
    const type = params.type || params.operationType || '';
    const filtered = mockLogs.filter((item) => !type || matchesLogType(item.type, type));
    return pageOf(filtered, withDefaultSize(params, 8));
  }
  const pageParams = withDefaultSize(params, 8);
  const response = await apiClient.get<PageResult<AdminLog>>('/admin/logs', { params: pageParams });
  return normalizePage(response.data, pageParams);
}

export async function approveResource(id: RawId) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.auditRows, id, '已通过', 'PUBLISHED');
  return apiClient.post(`/admin/resources/${id}/approve`);
}

export async function rejectResource(id: RawId, reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.auditRows, id, '已驳回', 'REJECTED');
  return apiClient.post(`/admin/resources/${id}/reject`, { reason });
}

export async function offlineResource(id: RawId, reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.resourceRows, id, '已下架', 'OFFLINE');
  return apiClient.post(`/admin/resources/${id}/offline`, { reason });
}

export async function restoreResource(id: RawId, reason = '管理员恢复资源') {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.resourceRows, id, '已发布', 'PUBLISHED');
  return apiClient.post(`/admin/resources/${id}/restore`, { reason });
}

export async function copyrightDownResource(id: RawId, reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.resourceRows, id, '版权下架', 'COPYRIGHT_DOWN');
  return apiClient.post(`/admin/resources/${id}/copyright-down`, { reason });
}

export async function deleteResource(id: RawId, reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.resourceRows, id, '已删除', 'DELETED');
  return apiClient.delete(`/admin/resources/${id}`, { data: { reason } });
}

export async function closeRequest(id: RawId, reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.requestRows, id, '已关闭', 'CLOSED');
  return apiClient.post(`/admin/requests/${id}/close`, { reason });
}

export async function deleteReply(id: RawId) {
  if (ENABLE_MOCKS) return { ok: true, id };
  return apiClient.delete(`/admin/replies/${id}`);
}

export async function hideComment(id: RawId) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.commentRows, id, '已隐藏', 'HIDDEN');
  return apiClient.post(`/admin/comments/${id}/hide`);
}

export async function deleteComment(id: RawId) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.commentRows, id, '已删除', 'DELETED');
  return apiClient.delete(`/admin/comments/${id}`);
}

export async function restoreComment(id: RawId) {
  if (ENABLE_MOCKS) return setRowStatus(mockContent.commentRows, id, '正常', 'ACTIVE');
  return apiClient.post(`/admin/comments/${id}/restore`);
}

export async function disableUser(id: RawId, reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockUsers, id, '已禁用', 'DISABLED');
  return apiClient.put(`/admin/members/${id}/disable`, { reason });
}

export async function enableUser(id: RawId) {
  if (ENABLE_MOCKS) return setRowStatus(mockUsers, id, '正常', 'NORMAL');
  return apiClient.put(`/admin/members/${id}/enable`);
}

export async function handleReport(id: RawId, status: 'RESOLVED' | 'REJECTED', reason: string, action?: AdminReportAction) {
  if (ENABLE_MOCKS) return setRowStatus([...mockCompliance.reports, ...mockCompliance.complaints], id, status === 'RESOLVED' ? '已处理' : '已驳回', status);
  return apiClient.post(`/admin/reports/${id}/handle`, { status, action, reason, handleResult: reason });
}

export async function handleAppeal(id: RawId, status: 'APPROVED' | 'REJECTED', reason: string) {
  if (ENABLE_MOCKS) return setRowStatus(mockCompliance.appeals, id, status === 'APPROVED' ? '已处理' : '已驳回', status);
  return apiClient.post(`/admin/appeals/${id}/handle`, { status, reason, handleResult: reason });
}

export async function createCategory(payload: CatalogCreatePayload) {
  if (ENABLE_MOCKS) {
    const parent = payload.parentId ? findCatalogCategory(payload.parentId) : undefined;
    const row = appendCatalogRow({
      name: payload.name,
      type: payload.level === 2 ? '二级分类' : '一级分类',
      kind: 'CATEGORY',
      parent: parent?.name || '-',
      level: payload.level || 1,
      parentId: payload.parentId,
      sortOrder: payload.sortOrder,
    });
    mockCatalog.categories.push(row);
    return row;
  }
  return apiClient.post('/admin/categories', payload);
}

export async function updateCategory(id: RawId, payload: Partial<AdminCategory> & { status?: string }) {
  if (ENABLE_MOCKS) return updateCatalogRow(id, payload);
  return apiClient.put(`/admin/categories/${id}`, payload);
}

export async function disableCategory(id: RawId) {
  if (ENABLE_MOCKS) return updateCatalogRow(id, { status: '禁用', rawStatus: 'DISABLED' });
  return apiClient.put(`/admin/categories/${id}/disable`);
}

export async function createTag(name: string) {
  if (ENABLE_MOCKS) {
    const options = mockCatalogOptions();
    if (!options.tagCandidates.some((item) => item.name === name)) {
      throw new Error('标签必须来自一级分类、二级分类或资源类型规范词');
    }
    const existing = mockCatalog.tags.find((item) => item.name === name);
    if (existing) {
      if (existing.rawStatus === 'DISABLED' || existing.status === '禁用') {
        Object.assign(existing, { status: '启用', rawStatus: 'ENABLED' });
      }
      return clone(existing);
    }
    const row = appendCatalogRow({ name, type: '标签', kind: 'TAG' });
    mockCatalog.tags.push(row);
    return row;
  }
  return apiClient.post('/admin/tags', { name });
}

export async function backfillNormativeTags() {
  if (ENABLE_MOCKS) {
    const options = mockCatalogOptions();
    const created: string[] = [];
    options.missingTags.forEach((candidate) => {
      const row = appendCatalogRow({ name: candidate.name, type: '标签', kind: 'TAG' });
      mockCatalog.tags.push(row);
      created.push(candidate.name);
    });
    return { createdCount: created.length, createdTags: created };
  }
  const response = await apiClient.post<{ createdCount: number; createdTags: string[] }>('/admin/tags/backfill');
  return response.data;
}

export async function updateTag(id: RawId, payload: Partial<AdminCategory> & { status?: string }) {
  if (ENABLE_MOCKS) return updateCatalogRow(id, payload);
  return apiClient.put(`/admin/tags/${id}`, payload);
}

export async function disableTag(id: RawId) {
  if (ENABLE_MOCKS) return updateCatalogRow(id, { status: '禁用', rawStatus: 'DISABLED' });
  return apiClient.put(`/admin/tags/${id}/disable`);
}

export async function mergeTags(sourceTagId: RawId, targetTagId: RawId) {
  if (ENABLE_MOCKS) return updateCatalogRow(sourceTagId, { status: '禁用', rawStatus: 'DISABLED' });
  return apiClient.post('/admin/tags/merge', { sourceTagId, targetTagId });
}

export async function updateMemberLevel(level: MemberLevel) {
  const id = level.rawId || level.id;
  if (ENABLE_MOCKS) {
    const index = mockConfig.memberLevels.findIndex((item) => sameId(item.rawId || item.id, id));
    if (index >= 0) mockConfig.memberLevels[index] = { ...mockConfig.memberLevels[index], ...level };
    return level;
  }
  return apiClient.put(`/admin/config/member-levels/${id}`, level);
}

export async function updateConfigItem(item: ConfigItem) {
  if (ENABLE_MOCKS) return item;
  return apiClient.put('/admin/config', { key: item.key || item.label, value: item.value, valueType: item.valueType || 'STRING' });
}

export async function refreshConfigCache() {
  if (ENABLE_MOCKS) return { ok: true, status: 'REFRESHED' };
  return apiClient.post('/admin/cache/refresh');
}

function withContentDefaults(data: Partial<AdminContentData>): AdminContentData {
  return {
    auditRows: data.auditRows || [],
    resourceRows: data.resourceRows || [],
    requestRows: data.requestRows || [],
    commentRows: data.commentRows || [],
  };
}

function withComplianceDefaults(data: Partial<AdminComplianceData>): AdminComplianceData {
  return {
    reports: data.reports || [],
    complaints: data.complaints || [],
    appeals: data.appeals || [],
  };
}

function withCatalogDefaults(data: Partial<AdminCatalogData>): AdminCatalogData {
  return {
    categories: data.categories || [],
    tags: data.tags || [],
    rows: data.rows || [],
  };
}

function withCatalogOptionsDefaults(data: Partial<AdminCatalogOptions>): AdminCatalogOptions {
  return {
    firstLevelCategories: data.firstLevelCategories || [],
    secondLevelCategories: data.secondLevelCategories || [],
    resourceTypes: data.resourceTypes || [],
    tagCandidates: data.tagCandidates || [],
    missingTags: data.missingTags || [],
  };
}

function contentSectionRows(section: AdminContentSection, data: AdminContentData): Array<AdminResource | AdminRequestPost | AdminComment> {
  if (section === 'audit') return data.auditRows;
  if (section === 'resource') return data.resourceRows;
  if (section === 'request') return data.requestRows;
  return data.commentRows;
}

function complianceSectionRows(section: AdminComplianceSection, data: AdminComplianceData): Array<AdminReport | AdminComplaint | AdminAppeal> {
  if (section === 'report') return data.reports;
  if (section === 'copyright') return data.complaints;
  return data.appeals;
}

function catalogSectionRows(section: AdminCatalogSection, data: AdminCatalogData): AdminCategory[] {
  if (section === 'category') return data.categories;
  if (section === 'tag') return data.tags;
  return data.rows.length ? data.rows : [...data.categories, ...data.tags];
}

function filterCatalogRows(rows: AdminCategory[], params: Record<string, string>): AdminCategory[] {
  const keyword = params.keyword?.trim();
  const status = params.status?.trim();
  const level = params.level?.trim();
  const parentId = params.parentId?.trim();
  return rows.filter((item) => {
    const itemLevel = item.level || (item.type === '一级分类' ? 1 : item.type === '二级分类' ? 2 : undefined);
    const matchesKeyword = !keyword || item.name.includes(keyword);
    const matchesStatus = !status || item.rawStatus === status || item.status === status || item.status === enabledStatusLabel(status);
    const matchesLevel = !level || String(itemLevel || '') === level;
    const matchesParent = !parentId || String(item.parentId || '') === parentId;
    return matchesKeyword && matchesStatus && matchesLevel && matchesParent;
  });
}

function isPageLike<T>(data: unknown): data is PageResult<T> {
  return Boolean(
    data &&
      typeof data === 'object' &&
      ('list' in data || 'items' in data || 'total' in data || 'page' in data || 'size' in data || 'pageSize' in data),
  );
}

function normalizePage<T>(data: Partial<PageResult<T>> | undefined, params: Record<string, string> = {}): PageResult<T> {
  const list = Array.isArray(data?.list) ? data.list : Array.isArray(data?.items) ? data.items : [];
  const fallbackPage = positiveNumber(params.page, 1);
  const fallbackSize = positiveNumber(params.size || params.pageSize, 20);
  const page = positiveNumber(data?.page, fallbackPage);
  const size = positiveNumber(data?.size || data?.pageSize, fallbackSize);
  const total = positiveNumber(data?.total, list.length);
  return {
    ...data,
    total,
    list,
    items: list,
    page,
    size,
    pageSize: size,
  };
}

function pageOf<T>(items: T[], params: Record<string, string>): PageResult<T> {
  const page = positiveNumber(params.page, 1);
  const size = positiveNumber(params.size || params.pageSize, 20);
  const list = clone(items).slice((page - 1) * size, page * size);
  return { total: items.length, list, items: list, page, size, pageSize: size };
}

function positiveNumber(value: unknown, fallback: number) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : fallback;
}

function withDefaultSize(params: Record<string, string>, size: number): Record<string, string> {
  return {
    ...params,
    page: params.page || '1',
    size: params.size || params.pageSize || String(size),
  };
}

function setRowStatus<T extends { id: string; rawId?: RawId; status: string; rawStatus?: string }>(rows: T[], id: RawId, status: string, rawStatus: string) {
  const row = rows.find((item) => sameId(item.rawId || item.id, id));
  if (row) {
    row.status = status;
    row.rawStatus = rawStatus;
  }
  return clone(row || { id, status, rawStatus });
}

function appendCatalogRow(partial: Pick<AdminCategory, 'name' | 'type' | 'kind'> & Partial<AdminCategory>): AdminCategory {
  const id = partial.kind === 'TAG' ? `T${String(mockCatalog.tags.length + 1).padStart(3, '0')}` : `F${String(mockCatalog.categories.length + 1).padStart(3, '0')}`;
  const row: AdminCategory = {
    id,
    rawId: id,
    name: partial.name,
    type: partial.type,
    parent: partial.parent || '-',
    relationCount: 0,
    status: '启用',
    rawStatus: 'ENABLED',
    kind: partial.kind,
    level: partial.level,
    parentId: partial.parentId,
    sortOrder: partial.sortOrder,
  };
  mockCatalog.rows.push(row);
  return row;
}

function findCatalogCategory(id: RawId) {
  return mockCatalog.categories.find((item) => sameId(item.rawId || item.id, id));
}

function mockCatalogOptions(): AdminCatalogOptions {
  const firstLevelCategories = mockCatalog.categories
    .filter((item) => item.type === '一级分类')
    .map((item) => ({
      id: item.rawId || item.id,
      name: item.name,
      sortOrder: item.sortOrder,
      status: item.rawStatus || (item.status === '启用' ? 'ENABLED' : 'DISABLED'),
    }));
  const secondLevelCategories = mockCatalog.categories
    .filter((item) => item.type === '二级分类')
    .map((item) => ({
      id: item.rawId || item.id,
      parentId: item.parentId,
      parentName: item.parent,
      name: item.name,
      sortOrder: item.sortOrder,
      status: item.rawStatus || (item.status === '启用' ? 'ENABLED' : 'DISABLED'),
    }));
  const resourceTypes = [
    { code: 'DOCUMENT', name: '文档' },
    { code: 'SOFTWARE', name: '软件' },
    { code: 'SOURCE_CODE', name: '源码' },
    { code: 'MATERIAL', name: '素材' },
    { code: 'COURSE', name: '教程' },
    { code: 'TEMPLATE', name: '模板' },
    { code: 'LINK', name: '链接' },
  ];
  const names = [
    ...firstLevelCategories.filter((item) => item.status === 'ENABLED').map((item) => ({ name: item.name, source: '一级分类', sourceId: item.id })),
    ...secondLevelCategories.filter((item) => item.status === 'ENABLED').map((item) => ({ name: item.name, source: '二级分类', sourceId: item.id, parentId: item.parentId })),
    ...resourceTypes.map((item) => ({ name: item.name, source: '资源类型', sourceId: item.code })),
  ];
  const tagCandidates = names.map((item) => {
    const tag = mockCatalog.tags.find((tagItem) => tagItem.name === item.name);
    return {
      ...item,
      exists: Boolean(tag),
      tagId: tag?.rawId || tag?.id,
      status: tag?.rawStatus || (tag?.status === '禁用' ? 'DISABLED' : tag ? 'ENABLED' : 'MISSING'),
    };
  });
  return {
    firstLevelCategories,
    secondLevelCategories,
    resourceTypes,
    tagCandidates,
    missingTags: tagCandidates.filter((item) => !item.exists),
  };
}

function enabledStatusLabel(status: string) {
  if (status === 'ENABLED') return '启用';
  if (status === 'DISABLED') return '禁用';
  return status;
}

function updateCatalogRow(id: RawId, payload: Partial<AdminCategory>) {
  const row = mockCatalog.rows.find((item) => sameId(item.rawId || item.id, id));
  if (row) {
    Object.assign(row, payload);
    if (row.kind === 'TAG') {
      const tag = mockCatalog.tags.find((item) => sameId(item.rawId || item.id, id));
      if (tag) Object.assign(tag, row);
    } else {
      const category = mockCatalog.categories.find((item) => sameId(item.rawId || item.id, id));
      if (category) Object.assign(category, row);
    }
  }
  return clone(row || { id, ...payload });
}

function matchesLogType(actual: string, type: string) {
  const normalized = operationLabel(type);
  if (normalized === '资源上下架') return actual === '资源下架' || actual === '资源恢复';
  if (normalized === '用户状态变更') return actual === '用户禁用' || actual === '用户恢复';
  if (normalized === '评论管理') return actual === '评论删除' || actual === '评论恢复';
  if (normalized === '举报投诉处理') return actual === '举报处理' || actual === '版权投诉处理';
  if (normalized === '分类标签维护') return actual.startsWith('分类') || actual.startsWith('标签');
  return actual === normalized;
}

function operationLabel(type: string) {
  const labels: Record<string, string> = {
    ACCOUNT_LOGIN: '账号登录',
    RESOURCE_APPROVE: '资源审核',
    RESOURCE_REJECT: '资源驳回',
    RESOURCE_OFFLINE: '资源下架',
    RESOURCE_RESTORE: '资源恢复',
    RESOURCE_COPYRIGHT_DOWN: '版权投诉处理',
    RESOURCE_DELETE: '资源删除',
    MEMBER_DISABLED: '用户禁用',
    MEMBER_NORMAL: '用户恢复',
    COMMENT_HIDDEN: '评论隐藏',
    COMMENT_DELETED: '评论删除',
    COMMENT_ACTIVE: '评论恢复',
    REPORT_HANDLE: '举报处理',
    APPEAL_HANDLE: '申诉处理',
    CATEGORY_CREATE: '分类新增',
    CATEGORY_UPDATE: '分类修改',
    CATEGORY_ENABLE: '分类启用',
    CATEGORY_DISABLE: '分类禁用',
    TAG_BACKFILL: '标签补齐',
    TAG_CREATE: '标签新增',
    TAG_UPDATE: '标签修改',
    TAG_ENABLE: '标签启用',
    TAG_DISABLE: '标签禁用',
    TAG_MERGE: '标签合并',
    MEMBER_LEVEL_UPDATE: '等级配置',
    SYSTEM_CONFIG_UPDATE: '系统参数',
    CACHE_REFRESH: '缓存刷新',
  };
  return labels[type] || type;
}

function sameId(left: RawId | undefined, right: RawId | undefined) {
  return String(left) === String(right);
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}
