export type AdminResource = {
  id: string;
  title: string;
  user?: string;
  status: string;
};

export type AdminRequestPost = {
  id: string;
  title: string;
  status: string;
};

export type AdminComment = {
  id: string;
  content: string;
  target: string;
  status: string;
};

export type AdminUser = {
  id: string;
  nickname: string;
  registeredAt: string;
  status: string;
};

export type AdminReport = {
  id: string;
  targetId: string;
  target: string;
  type: string;
  status: string;
  action: 'delete-comment' | 'offline-resource' | 'close-request';
};

export type AdminComplaint = {
  id: string;
  resourceId: string;
  resourceName: string;
  complainant: string;
  status: string;
};

export type AdminCategory = {
  id: string;
  name: string;
  type: string;
  parent: string;
  relationCount: number;
  status: string;
};

export type AdminLog = {
  time: string;
  adminId: string;
  ip: string;
  type: string;
  target: string;
  targetId: string;
  before: string;
  after: string;
  result: string;
};

export type MemberLevel = {
  name: string;
  min: string;
  max: string;
  downloads: string;
  files: string;
  rewardLimit: string;
  canTop: string;
};
