export type Category = {
  id: string;
  name: string;
  children: Array<{ id: string; name: string }>;
};

export type User = {
  id: number;
  username: string;
  nickname: string;
  email: string;
  emailVerified: boolean;
  bio: string;
  contact: string;
  avatar: string;
  level: string;
  points: number;
  frozenPoints: number;
  availablePoints: number;
  rewardLimit: number;
  expNeeded: number;
  upgradeProgress?: number;
  progressPercent?: number;
  levelCode?: string;
  levelInfo?: MemberLevelInfo;
  levelMinPoints?: number;
  levelMaxPoints?: number | null;
  nextLevel?: string;
  nextLevelMinPoints?: number;
  dailyDownloadLimit?: number;
  dailyResourcePublishLimit?: number;
  dailyRequestPublishLimit?: number;
  maxFilesPerResource?: number;
  maxFileSizeMb?: number;
  canApplyTop?: boolean;
  benefits?: MemberBenefit[];
  pointRules?: PointRule[];
  rules?: PointRule[];
  passwordUpdatedAt: string;
};

export type MemberLevelInfo = {
  code?: string;
  name?: string;
  minPoints?: number;
  maxPoints?: number | null;
};

export type MemberBenefit = {
  name: string;
  description?: string;
  limit: string | number | boolean;
  enabled?: boolean;
};

export type PointRule = {
  key?: string;
  action: string;
  points: string;
  note: string;
};

export type PointAccount = Pick<
  User,
  | 'points'
  | 'frozenPoints'
  | 'availablePoints'
  | 'level'
  | 'rewardLimit'
  | 'expNeeded'
  | 'upgradeProgress'
  | 'progressPercent'
  | 'levelCode'
  | 'levelInfo'
  | 'levelMinPoints'
  | 'levelMaxPoints'
  | 'nextLevel'
  | 'nextLevelMinPoints'
  | 'dailyDownloadLimit'
  | 'dailyResourcePublishLimit'
  | 'dailyRequestPublishLimit'
  | 'maxFilesPerResource'
  | 'maxFileSizeMb'
  | 'canApplyTop'
  | 'benefits'
  | 'pointRules'
  | 'rules'
>;

export type PointFlow = {
  id: number;
  flowType: string;
  scene: string;
  sceneLabel?: string;
  pointsChange: number;
  frozenChange: number;
  beforePoints: number;
  afterPoints: number;
  beforeFrozenPoints: number;
  afterFrozenPoints: number;
  relatedType?: string;
  relatedId?: number;
  relatedLabel?: string;
  description?: string;
  balanceText?: string;
  createTime: string;
};

export type ResourceAttachment = {
  id: number;
  name: string;
  size: string;
  type: string;
  downloads: number;
};

export type Resource = {
  id: number;
  title: string;
  description: string;
  detail: string;
  category1: string;
  category2: string;
  type: string;
  author: string;
  downloads: number;
  score: number;
  date: string;
  tags: string[];
  fileName: string;
  fileSize: string;
  attachments: ResourceAttachment[];
  liked: boolean;
  favorited: boolean;
  userRating: number;
  ratingCount: number;
};

export type DemandStatus = 'active' | 'solved';
export type RewardType = 'FREE' | 'POINT';

export type Demand = {
  id: number;
  title: string;
  description: string;
  category1: string;
  category2: string;
  rewardType?: RewardType;
  points: number;
  replyCount: number;
  author: string;
  date: string;
  status: DemandStatus;
  tags: string[];
  format: string;
};

export type Comment = {
  id: number;
  author: string;
  content: string;
  date: string;
  mine?: boolean;
  accepted?: boolean;
  replies?: Comment[];
};

export type ReportTarget = 'RESOURCE' | 'DEMAND' | 'COMMENT' | 'COPYRIGHT';

export type ListParams = {
  keyword?: string;
  cate1?: string;
  cate2?: string;
  type?: string;
  status?: string;
  points?: string;
  sort?: string;
  order?: string;
  page?: number;
  pageSize?: number;
};

export type PagedResult<T> = {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
};

export type ProfileSummary = {
  resources: Resource[];
  demands: Demand[];
  favorites: Resource[];
  likes: Resource[];
  messages: Array<{ id: number; title: string; content: string; unread: boolean; date: string }>;
  loginLogs: Array<{ id: number; ip: string; device: string; location: string; time: string }>;
};
