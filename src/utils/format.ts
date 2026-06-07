export function formatCategory(category1?: string, category2?: string) {
  return [category1, category2].filter(Boolean).join(' / ') || '未分类';
}
