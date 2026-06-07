import type {
  AdminCategory,
  AdminComment,
  AdminComplaint,
  AdminLog,
  AdminReport,
  AdminRequestPost,
  AdminResource,
  AdminUser,
  MemberLevel,
} from '../types';

export const pendingResources: AdminResource[] = [
  { id: 'R001', title: 'UI设计全套模板', user: 'user001', status: '待审核' },
  { id: 'R002', title: '办公表格合集', user: 'user005', status: '待审核' },
  { id: 'R003', title: '往期驳回素材', user: 'user003', status: '已驳回' },
];

export const managedResources: AdminResource[] = [
  { id: 'R004', title: '编程入门教程', status: '已发布' },
  { id: 'R005', title: '违规影音文件', status: '已下架' },
  { id: 'R006', title: '版权投诉下架文件', status: '版权下架' },
];

export const requestPosts: AdminRequestPost[] = [
  { id: 'Q001', title: '求考研历年真题', status: '进行中' },
  { id: 'Q002', title: '违规资源求购帖', status: '已关闭' },
  { id: 'Q003', title: '闲置书籍互换求助', status: '进行中' },
];

export const comments: AdminComment[] = [
  { id: 'C001', content: '恶意辱骂违规言论', target: 'R001', status: '正常' },
  { id: 'C002', content: '已删除历史评论', target: 'R004', status: '已删除' },
  { id: 'C003', content: '广告引流灌水内容', target: 'R005', status: '正常' },
];

export const users: AdminUser[] = [
  { id: 'U001', nickname: '清风徐来', registeredAt: '2026-03-12', status: '正常' },
  { id: 'U002', nickname: '肆意妄为', registeredAt: '2026-04-05', status: '已禁用' },
  { id: 'U003', nickname: '合规用户', registeredAt: '2026-05-18', status: '正常' },
];

export const reports: AdminReport[] = [
  { id: 'J001', targetId: 'C001', target: '违规评论', type: '违规言论', status: '待处理', action: 'delete-comment' },
  { id: 'J002', targetId: 'R004', target: '违规资源', type: '违规资源', status: '待处理', action: 'offline-resource' },
  { id: 'J003', targetId: 'Q001', target: '违规求助帖', type: '违规帖子', status: '已处理', action: 'close-request' },
  { id: 'J004', targetId: 'Q005', target: '不良话题帖', type: '违规帖子', status: '待处理', action: 'close-request' },
];

export const complaints: AdminComplaint[] = [
  { id: 'B001', resourceId: 'R006', resourceName: '侵权课件', complainant: '版权方A', status: '待审核' },
  { id: 'B002', resourceId: 'R007', resourceName: '侵权素材', complainant: '版权方B', status: '已驳回' },
  { id: 'B003', resourceId: 'R008', resourceName: '侵权教程', complainant: '版权方C', status: '已处理' },
];

export const categories: AdminCategory[] = [
  { id: 'F001', name: '设计素材', type: '一级分类', parent: '-', relationCount: 126, status: '启用' },
  { id: 'F002', name: '学习教程', type: '一级分类', parent: '-', relationCount: 289, status: '启用' },
  { id: 'F003', name: 'UI模板', type: '二级分类', parent: '设计素材', relationCount: 68, status: '启用' },
  { id: 'F004', name: 'PSD素材', type: '二级分类', parent: '设计素材', relationCount: 42, status: '禁用' },
  { id: 'T001', name: '剪辑', type: '标签', parent: '-', relationCount: 105, status: '启用' },
  { id: 'T002', name: '办公', type: '标签', parent: '-', relationCount: 76, status: '禁用' },
];

export const memberLevels: MemberLevel[] = [
  { name: '普通会员', min: '0', max: '99', downloads: '5', files: '3', rewardLimit: '100', canTop: '否' },
  { name: '活跃会员', min: '100', max: '499', downloads: '10', files: '5', rewardLimit: '300', canTop: '否' },
  { name: '优质会员', min: '500', max: '1999', downloads: '20', files: '8', rewardLimit: '800', canTop: '是' },
  { name: '资深会员', min: '2000', max: '无上限', downloads: '50', files: '10', rewardLimit: '2000', canTop: '是' },
];

export const logs: AdminLog[] = [
  {
    time: '2026-05-24 08:10:22',
    adminId: 'AD001',
    ip: '192.168.1.101',
    type: '账号登录',
    target: '管理员账号',
    targetId: 'AD001',
    before: '未登录',
    after: '正常登录后台',
    result: '执行成功',
  },
  {
    time: '2026-05-24 09:05:16',
    adminId: 'AD002',
    ip: '192.168.1.102',
    type: '资源审核',
    target: '共享资源',
    targetId: 'RS20260418',
    before: '待审核',
    after: '审核通过公开',
    result: '执行成功',
  },
  {
    time: '2026-05-24 09:42:38',
    adminId: 'AD001',
    ip: '192.168.1.101',
    type: '资源下架',
    target: '共享资源',
    targetId: 'RS20260325',
    before: '正常展示',
    after: '违规临时下架',
    result: '执行成功',
  },
  {
    time: '2026-05-24 10:12:09',
    adminId: 'AD003',
    ip: '192.168.1.103',
    type: '资源恢复',
    target: '共享资源',
    targetId: 'RS20260325',
    before: '已下架',
    after: '恢复正常展示',
    result: '执行成功',
  },
  {
    time: '2026-05-24 10:56:21',
    adminId: 'AD002',
    ip: '192.168.1.102',
    type: '用户禁用',
    target: '平台用户',
    targetId: 'US10086',
    before: '正常可用',
    after: '违规账号禁用',
    result: '执行成功',
  },
  {
    time: '2026-05-24 11:20:45',
    adminId: 'AD001',
    ip: '192.168.1.101',
    type: '用户恢复',
    target: '平台用户',
    targetId: 'US10086',
    before: '账号禁用',
    after: '解除限制恢复使用',
    result: '执行成功',
  },
  {
    time: '2026-05-24 13:18:33',
    adminId: 'AD004',
    ip: '192.168.1.104',
    type: '评论删除',
    target: '用户评论',
    targetId: 'CM3692',
    before: '正常展示',
    after: '删除违规评论',
    result: '执行成功',
  },
  {
    time: '2026-05-24 13:55:17',
    adminId: 'AD003',
    ip: '192.168.1.103',
    type: '评论恢复',
    target: '用户评论',
    targetId: 'CM3692',
    before: '已删除',
    after: '恢复合法评论',
    result: '执行成功',
  },
  {
    time: '2026-05-24 14:26:08',
    adminId: 'AD002',
    ip: '192.168.1.102',
    type: '举报处理',
    target: '用户举报单',
    targetId: 'JB5201',
    before: '待处理',
    after: '核实无效关闭工单',
    result: '执行成功',
  },
  {
    time: '2026-05-24 14:58:42',
    adminId: 'AD001',
    ip: '192.168.1.101',
    type: '版权投诉处理',
    target: '版权投诉单',
    targetId: 'TQ8812',
    before: '待核验',
    after: '判定侵权处置资源',
    result: '执行成功',
  },
  {
    time: '2026-05-24 15:22:36',
    adminId: 'AD004',
    ip: '192.168.1.104',
    type: '分类新增',
    target: '二级分类',
    targetId: 'FL0218',
    before: '无分类数据',
    after: '新增插画设计分类',
    result: '执行成功',
  },
  {
    time: '2026-05-24 15:47:19',
    adminId: 'AD003',
    ip: '192.168.1.103',
    type: '标签修改',
    target: '资源标签',
    targetId: 'BQ1066',
    before: '影视剪辑',
    after: '短视频剪辑',
    result: '执行成功',
  },
  {
    time: '2026-05-24 16:10:54',
    adminId: 'AD002',
    ip: '192.168.1.102',
    type: '分类删除',
    target: '一级分类',
    targetId: 'FL0009',
    before: '正常启用',
    after: '删除闲置无用分类',
    result: '执行成功',
  },
];
