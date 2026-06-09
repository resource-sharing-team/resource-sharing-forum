import type { Category } from '../types';

export const categories: Category[] = [
  {
    id: '1',
    name: '文档资料',
    children: [
      { id: '11', name: '考试资料' },
      { id: '12', name: '办公模板' },
      { id: '13', name: '学习笔记' },
    ],
  },
  {
    id: '2',
    name: '设计素材',
    children: [
      { id: '21', name: 'UI设计' },
      { id: '22', name: '图片素材' },
      { id: '23', name: '字体图标' },
    ],
  },
  {
    id: '3',
    name: '源码模板',
    children: [
      { id: '31', name: '前端源码' },
      { id: '32', name: '后端源码' },
      { id: '33', name: '完整项目' },
    ],
  },
  {
    id: '4',
    name: '教程学习',
    children: [
      { id: '41', name: 'IT教程' },
      { id: '42', name: '办公教程' },
      { id: '43', name: '设计教程' },
    ],
  },
  {
    id: '5',
    name: '软件工具',
    children: [
      { id: '51', name: '开发工具' },
      { id: '52', name: '设计工具' },
      { id: '53', name: '效率工具' },
    ],
  },
];

export const resourceTypes = ['文档', '软件', '源码', '素材', '教程', '模板', '链接'];

export function getParentCategoryId(category2?: string) {
  if (!category2) return undefined;
  return categories.find((category) => category.children.some((child) => child.id === category2))?.id;
}

export function normalizeCategorySelection(category1?: string, category2?: string) {
  const normalizedCategory2 = category2 || undefined;
  const inferredCategory1 = normalizedCategory2 ? getParentCategoryId(normalizedCategory2) : undefined;
  const requestedParent = categories.find((category) => category.id === category1);
  const normalizedCategory1 = requestedParent?.id || inferredCategory1;
  const parent = categories.find((category) => category.id === normalizedCategory1);
  const childBelongsToParent = parent?.children.some((child) => child.id === normalizedCategory2);

  return {
    cate1: parent?.id,
    cate2: childBelongsToParent ? normalizedCategory2 : undefined,
  };
}

export function getCategoryName(category1?: string, category2?: string) {
  const parent = categories.find((item) => item.id === category1);
  const child = parent?.children.find((item) => item.id === category2);
  return [parent?.name, child?.name].filter(Boolean).join(' / ') || '未分类';
}
