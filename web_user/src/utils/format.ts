import type { Category, DemandStatus } from '../types';

export function formatCategory(category1?: string, category2?: string, categories: Category[] = []) {
  const parent = categories.find((item) => item.id === category1 || item.children.some((child) => child.id === category2));
  const child = parent?.children.find((item) => item.id === category2);
  const parentLabel = parent?.name || category1;
  const childLabel = child?.name || category2;
  return [parentLabel, childLabel].filter(Boolean).join(' > ') || '未分类';
}

export function demandStatusLabel(status?: DemandStatus) {
  const labels: Record<DemandStatus, string> = {
    active: '进行中',
    solved: '已解决',
    cancelled: '已取消',
    closed: '已关闭',
  };
  return status ? labels[status] : '未知';
}

export function canReplyToDemand(status?: DemandStatus) {
  return status === 'active';
}
