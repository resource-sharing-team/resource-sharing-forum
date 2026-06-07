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
  expNeeded: number;
  passwordUpdatedAt: string;
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

export type Demand = {
  id: number;
  title: string;
  description: string;
  category1: string;
  category2: string;
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
  sort?: string;
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
