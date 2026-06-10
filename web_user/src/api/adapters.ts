import type {
  Announcement,
  Category,
  DownloadInfo,
  NotificationMessage,
  LoginLog,
  PagedResult,
  ProfileSummary,
  Resource,
  ResourceAttachment,
  ResourceTypeOption,
  User,
  Comment,
  Demand,
} from '../types';

type RawRecord = Record<string, unknown>;

const typeLabels: Record<string, string> = {
  DOCUMENT: '\u6587\u6863',
  document: '\u6587\u6863',
  SOFTWARE: '\u8f6f\u4ef6',
  software: '\u8f6f\u4ef6',
  SOURCE_CODE: '\u6e90\u7801',
  source: '\u6e90\u7801',
  MATERIAL: '\u7d20\u6750',
  material: '\u7d20\u6750',
  COURSE: '\u6559\u7a0b',
  course: '\u6559\u7a0b',
  TEMPLATE: '\u6a21\u677f',
  template: '\u6a21\u677f',
  LINK: '\u94fe\u63a5',
  link: '\u94fe\u63a5',
};

export function mapUser(value: unknown): User {
  const raw = asRecord(value);
  const points = number(raw.points);
  const rawExpNeeded = number(raw.expNeeded, Math.max(1000, points));
  const expNeeded = rawExpNeeded <= points ? points + rawExpNeeded : rawExpNeeded;

  return {
    id: number(raw.id),
    username: text(raw.username, 'user'),
    nickname: text(first(raw.nickname, raw.username), 'user'),
    email: text(raw.email),
    emailVerified: bool(raw.emailVerified, true),
    bio: text(raw.bio),
    contact: text(first(raw.contact, raw.email)),
    avatar: text(raw.avatar),
    level: text(raw.level, 'Member'),
    points,
    expNeeded,
    passwordUpdatedAt: text(first(raw.passwordUpdatedAt, raw.passwordChangedAt), today()),
  };
}

export function mapResource(value: unknown): Resource {
  const raw = asRecord(value);
  const attachments = array(raw.attachments).map(mapAttachment);
  const fallbackAttachment = attachmentFromResource(raw);
  const safeAttachments = attachments.length ? attachments : fallbackAttachment ? [fallbackAttachment] : [];

  return {
    id: number(raw.id),
    title: text(raw.title, '\u672a\u547d\u540d\u8d44\u6e90'),
    description: text(first(raw.description, raw.summary)),
    detail: text(first(raw.detail, raw.description, raw.summary)),
    category1: text(first(raw.category1, raw.category1Id, raw.parentCategoryId)),
    category2: text(first(raw.category2, raw.categoryId, raw.category2Id)),
    type: resourceTypeLabel(first(raw.type, raw.resourceType)),
    author: mapAuthor(first(raw.author, raw.authorName, raw.uploaderName)),
    downloads: number(first(raw.downloads, raw.downloadCount)),
    score: number(first(raw.score, raw.averageRating)),
    date: text(first(raw.date, raw.publishedAt, raw.createdAt, raw.createTime), today()),
    tags: stringList(raw.tags),
    fileName: text(first(raw.fileName, safeAttachments[0]?.name)),
    fileSize: text(first(raw.fileSize, safeAttachments[0]?.size)),
    attachments: safeAttachments,
    liked: bool(first(raw.liked, raw.isLiked)),
    favorited: bool(first(raw.favorited, raw.isFavorited, raw.favorite)),
    userRating: number(raw.userRating),
    ratingCount: number(raw.ratingCount),
    status: text(raw.status, 'PUBLISHED'),
  };
}

export function mapDemand(value: unknown): Demand {
  const raw = asRecord(value);
  const status = text(raw.status).toUpperCase();

  return {
    id: number(raw.id),
    title: text(raw.title, '\u672a\u547d\u540d\u6c42\u8d44\u6e90'),
    description: text(first(raw.description, raw.content)),
    category1: text(first(raw.category1, raw.category1Id, raw.parentCategoryId)),
    category2: text(first(raw.category2, raw.categoryId, raw.category2Id)),
    points: number(first(raw.points, raw.rewardPoints)),
    replyCount: number(first(raw.replyCount, raw.answerCount, raw.commentCount)),
    author: mapAuthor(first(raw.author, raw.authorName)),
    date: text(first(raw.date, raw.createdAt, raw.createTime), today()),
    status: demandStatus(status),
    tags: stringList(raw.tags),
    format: text(first(raw.format, raw.expectedFormat), '\u4e0d\u9650'),
  };
}

function demandStatus(status: string) {
  if (status === 'RESOLVED' || status === 'SOLVED') return 'solved';
  if (status === 'CANCELLED' || status === 'CANCELED') return 'cancelled';
  if (status === 'CLOSED') return 'closed';
  return 'active';
}

export function mapComment(value: unknown): Comment {
  const raw = asRecord(value);
  return {
    id: number(raw.id),
    parentId: optionalNumber(first(raw.parentId, raw.parentCommentId)),
    resourceId: optionalNumber(first(raw.resourceId, raw.referencedResourceId)),
    externalUrl: text(first(raw.externalUrl, raw.url, raw.link)),
    author: mapAuthor(first(raw.author, raw.authorName, raw.nickname)),
    content: text(raw.content),
    date: text(first(raw.date, raw.createdAt, raw.createTime), today()),
    mine: bool(raw.mine, false),
    accepted: bool(raw.accepted, false),
    replyToAuthor: text(first(raw.replyToAuthor, raw.replyTo, raw.parentAuthor)),
    replies: array(raw.replies).map(mapComment),
  };
}

export function mapResourceDetail(value: unknown) {
  const raw = asRecord(value);
  return {
    resource: mapResource(first(raw.resource, raw)),
    comments: nestComments(array(raw.comments).map(mapComment)),
  };
}

export function mapDemandDetail(value: unknown) {
  const raw = asRecord(value);
  const replies = array(raw.replies).map(mapComment);
  const comments = array(raw.comments).map(mapComment);
  return {
    demand: mapDemand(first(raw.demand, raw.request, raw)),
    comments: nestComments(replies.length ? replies : comments),
  };
}

export function mapPaged<T>(value: unknown, mapper: (item: unknown) => T): PagedResult<T> {
  if (Array.isArray(value)) {
    return {
      items: value.map(mapper),
      total: value.length,
      page: 1,
      pageSize: value.length,
    };
  }
  const raw = asRecord(value);
  const list = array(first(raw.items, raw.list, raw.records, raw.content));
  return {
    items: list.map(mapper),
    total: number(raw.total, list.length),
    page: number(raw.page, 1),
    pageSize: number(first(raw.pageSize, raw.size), list.length || 10),
  };
}

export function mapProfileSummary(value: unknown): ProfileSummary {
  const raw = asRecord(value);
  return {
    resources: array(raw.resources).map(mapResource),
    demands: array(first(raw.demands, raw.requests)).map(mapDemand),
    favorites: array(raw.favorites).map(mapResource),
    likes: array(raw.likes).map(mapResource),
    messages: array(first(raw.messages, raw.notifications)).map(mapMessage),
    loginLogs: array(raw.loginLogs).map(mapLoginLog),
  };
}

export function mapMessages(value: unknown) {
  return mapPaged(value, mapMessage).items;
}

export function mapNotifications(value: unknown): PagedResult<NotificationMessage> {
  return mapPaged(value, mapMessage);
}

export function mapCategories(value: unknown): Category[] {
  return array(value).map(mapCategory).filter((item) => item.id && item.name);
}

export function mapResourceTypes(value: unknown): ResourceTypeOption[] {
  return array(value).map(mapResourceTypeOption).filter((item) => item.value && item.label);
}

export function mapAnnouncements(value: unknown): PagedResult<Announcement> {
  return mapPaged(value, mapAnnouncement);
}

export function mapLoginLogs(value: unknown): PagedResult<LoginLog> {
  return mapPaged(value, mapLoginLog);
}

export function mapDownloadInfo(value: unknown): DownloadInfo {
  const raw = asRecord(value);
  return {
    recordId: number(raw.recordId),
    fileName: text(raw.fileName),
    downloadUrl: text(raw.downloadUrl),
  };
}

function mapMessage(value: unknown) {
  const raw = asRecord(value);
  return {
    id: number(raw.id),
    title: text(raw.title, '\u7cfb\u7edf\u6d88\u606f'),
    content: text(raw.content),
    unread: raw.unread === undefined ? !bool(raw.read, false) : bool(raw.unread, false),
    date: text(first(raw.date, raw.createTime, raw.createdAt), today()),
  };
}

function mapLoginLog(value: unknown): LoginLog {
  const raw = asRecord(value);
  return {
    id: number(raw.id),
    ip: text(raw.ip),
    device: text(raw.device),
    location: text(raw.location),
    time: text(first(raw.time, raw.createTime, raw.createdAt), today()),
  };
}

function mapCategory(value: unknown): Category {
  const raw = asRecord(value);
  return {
    id: text(raw.id),
    name: text(first(raw.name, raw.categoryName, raw.category_name)),
    children: array(raw.children).map((child) => {
      const childRaw = asRecord(child);
      return {
        id: text(childRaw.id),
        name: text(first(childRaw.name, childRaw.categoryName, childRaw.category_name)),
      };
    }),
  };
}

function mapResourceTypeOption(value: unknown): ResourceTypeOption {
  if (typeof value === 'string') {
    return { value, label: resourceTypeLabel(value) };
  }
  const raw = asRecord(value);
  const optionValue = text(first(raw.value, raw.code, raw.type));
  return {
    value: optionValue,
    label: text(first(raw.label, raw.name, raw.title), resourceTypeLabel(optionValue)),
  };
}

function mapAnnouncement(value: unknown): Announcement {
  const raw = asRecord(value);
  return {
    id: number(raw.id),
    title: text(raw.title),
    content: text(raw.content),
    date: text(first(raw.date, raw.publishTime, raw.createTime, raw.createdAt), today()),
  };
}

function mapAttachment(value: unknown): ResourceAttachment {
  const raw = asRecord(value);
  return {
    id: number(raw.id),
    name: text(first(raw.name, raw.fileName, raw.originalFileName), '\u672a\u547d\u540d\u9644\u4ef6'),
    size: sizeText(first(raw.size, raw.fileSize)),
    type: text(first(raw.type, raw.fileType, raw.fileExt), '\u6587\u4ef6'),
    downloads: number(first(raw.downloads, raw.downloadCount)),
  };
}

function attachmentFromResource(raw: RawRecord): ResourceAttachment | null {
  const fileName = text(raw.fileName);
  if (!fileName) return null;
  return {
    id: number(first(raw.attachmentId, raw.fileId, raw.id)),
    name: fileName,
    size: sizeText(raw.fileSize),
    type: text(raw.fileType, '\u6587\u4ef6'),
    downloads: number(first(raw.downloads, raw.downloadCount)),
  };
}

function resourceTypeLabel(value: unknown) {
  const raw = text(value, 'DOCUMENT');
  return typeLabels[raw] || typeLabels[raw.toUpperCase()] || raw;
}

function mapAuthor(value: unknown) {
  const raw = asRecord(value);
  if (Object.keys(raw).length) {
    return text(first(raw.nickname, raw.username, raw.name), '\u533f\u540d\u7528\u6237');
  }
  return text(value, '\u533f\u540d\u7528\u6237');
}

function asRecord(value: unknown): RawRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as RawRecord) : {};
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function first(...values: unknown[]) {
  return values.find((value) => value !== undefined && value !== null && value !== '');
}

function text(value: unknown, fallback = '') {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value);
}

function number(value: unknown, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function optionalNumber(value: unknown) {
  if (value === undefined || value === null || value === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function bool(value: unknown, fallback = false) {
  if (typeof value === 'boolean') return value;
  if (value === 'true' || value === 1 || value === '1') return true;
  if (value === 'false' || value === 0 || value === '0') return false;
  return fallback;
}

function stringList(value: unknown) {
  if (Array.isArray(value)) return value.map((item) => String(item)).filter(Boolean);
  if (typeof value === 'string') {
    return value
      .split(/[,，]/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
}

function sizeText(value: unknown) {
  if (typeof value === 'number') {
    if (value <= 0) return '\u5f85\u5904\u7406';
    if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KB`;
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
  }
  return text(value, '\u5f85\u5904\u7406');
}

function nestComments(comments: Comment[]) {
  const byId = new Map<number, Comment>();
  const roots: Comment[] = [];

  comments.forEach((comment) => {
    byId.set(comment.id, { ...comment, replies: [...(comment.replies || [])] });
  });

  byId.forEach((comment) => {
    if (comment.parentId && byId.has(comment.parentId)) {
      const parent = byId.get(comment.parentId);
      parent?.replies?.push(comment);
      return;
    }
    roots.push(comment);
  });

  return roots;
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
