export type RawId = number | string;

export type PageResult<T> = {
  total: number;
  list?: T[];
  items?: T[];
  page: number;
  size?: number;
  pageSize?: number;
};

export type AdminSessionUser = {
  id: RawId;
  username?: string;
  nickname?: string;
  email?: string;
  role: string;
};

export type AdminSession = {
  token: string;
  role: string;
  expireAt?: string;
  user: AdminSessionUser;
};

export type AdminResource = {
  id: string;
  rawId?: RawId;
  title: string;
  user?: string;
  status: string;
  rawStatus?: string;
};

export type AdminRequestPost = {
  id: string;
  rawId?: RawId;
  title: string;
  user?: string;
  status: string;
  rawStatus?: string;
};

export type AdminComment = {
  id: string;
  rawId?: RawId;
  content: string;
  target: string;
  targetType?: string;
  targetId?: RawId;
  status: string;
  rawStatus?: string;
};

export type AdminUser = {
  id: string;
  rawId?: RawId;
  accountId?: RawId;
  username?: string;
  email?: string;
  nickname: string;
  registeredAt: string;
  status: string;
  rawStatus?: string;
};

export type AdminReportAction =
  | 'delete-comment'
  | 'offline-resource'
  | 'copyright-down-resource'
  | 'close-request'
  | 'delete-reply'
  | 'disable-user';

export type AdminReport = {
  id: string;
  rawId?: RawId;
  targetId: string;
  rawTargetId?: RawId;
  targetType?: string;
  target: string;
  type: string;
  reason?: string;
  status: string;
  rawStatus?: string;
  action: AdminReportAction;
};

export type AdminComplaint = {
  id: string;
  rawId?: RawId;
  resourceId: string;
  rawResourceId?: RawId;
  resourceName: string;
  complainant: string;
  status: string;
  rawStatus?: string;
};

export type AdminAppeal = {
  id: string;
  rawId?: RawId;
  targetId: string;
  targetType: string;
  appellant?: string;
  reason?: string;
  status: string;
  rawStatus?: string;
};

export type AdminCategory = {
  id: string;
  rawId?: RawId;
  name: string;
  type: string;
  parent: string;
  relationCount: number;
  status: string;
  rawStatus?: string;
  kind?: 'CATEGORY' | 'TAG';
  level?: number;
  parentId?: RawId;
  sortOrder?: number;
};

export type AdminLog = {
  id?: RawId;
  time: string;
  date?: string;
  adminId: RawId;
  ip: string;
  type: string;
  operationType?: string;
  target: string;
  targetType?: string;
  targetId: RawId;
  before: string;
  after: string;
  result: string;
};

export type MemberLevel = {
  id?: string;
  rawId?: RawId;
  name: string;
  min: string;
  max: string;
  downloads: string;
  resourcePublishLimit?: string;
  requestPublishLimit?: string;
  files: string;
  rewardLimit: string;
  canTop: string;
};

export type ConfigItem = {
  key?: string;
  label: string;
  value: string;
  valueType?: string;
};

export type AdminContentData = {
  auditRows: AdminResource[];
  resourceRows: AdminResource[];
  requestRows: AdminRequestPost[];
  commentRows: AdminComment[];
};

export type AdminContentSection = 'audit' | 'resource' | 'request' | 'comment';

export type AdminComplianceData = {
  reports: AdminReport[];
  complaints: AdminComplaint[];
  appeals: AdminAppeal[];
};

export type AdminComplianceSection = 'report' | 'copyright' | 'appeal';

export type AdminCatalogData = {
  categories: AdminCategory[];
  tags: AdminCategory[];
  rows: AdminCategory[];
};

export type AdminCatalogSection = 'all' | 'category' | 'tag';

export type AdminCatalogOptionCategory = {
  id: RawId;
  name: string;
  parentId?: RawId;
  parentName?: string;
  sortOrder?: number;
  status: string;
};

export type AdminCatalogTagCandidate = {
  name: string;
  source: '一级分类' | '二级分类' | '资源类型' | string;
  sourceId?: RawId;
  parentId?: RawId;
  exists: boolean;
  tagId?: RawId;
  status: string;
};

export type AdminCatalogOptions = {
  firstLevelCategories: AdminCatalogOptionCategory[];
  secondLevelCategories: AdminCatalogOptionCategory[];
  resourceTypes: Array<{ code: string; name: string }>;
  tagCandidates: AdminCatalogTagCandidate[];
  missingTags: AdminCatalogTagCandidate[];
};

export type AdminConfigData = {
  memberLevels: MemberLevel[];
  scoreRules: ConfigItem[];
  systemParams: ConfigItem[];
};
