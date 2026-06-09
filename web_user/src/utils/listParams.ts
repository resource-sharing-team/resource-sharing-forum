import { normalizeCategorySelection } from '../data/catalog';
import type { ListParams } from '../types';

type ListMode = 'resources' | 'demands';

export function resourceListParamsFromSearch(searchParams: URLSearchParams): ListParams {
  const common = commonListParamsFromSearch(searchParams);
  return {
    ...common,
    type: firstSearchParam(searchParams, 'type', 'resourceType') || undefined,
  };
}

export function demandListParamsFromSearch(searchParams: URLSearchParams): ListParams {
  const common = commonListParamsFromSearch(searchParams);
  return {
    ...common,
    status: firstSearchParam(searchParams, 'status') || undefined,
    points: firstSearchParam(searchParams, 'points', 'rewardPoints', 'pointsFilter') || undefined,
  };
}

export function listParamsToSearchParams(params: ListParams, mode: ListMode) {
  const next = new URLSearchParams();
  append(next, 'keyword', params.keyword);
  append(next, 'cate1', params.cate1);
  append(next, 'cate2', params.cate2);
  if (mode === 'resources') {
    append(next, 'type', params.type);
  } else {
    append(next, 'status', params.status);
    append(next, 'points', params.points);
  }
  if (params.sort && params.sort !== 'latest') next.set('sort', params.sort);
  if (mode === 'demands') {
    next.set('order', params.order === 'asc' ? 'asc' : 'desc');
  } else if (params.order && params.order !== 'desc') {
    next.set('order', params.order);
  }
  if (params.page && params.page > 1) next.set('page', String(params.page));
  if (params.pageSize && params.pageSize !== 5) next.set('pageSize', String(params.pageSize));
  return next;
}

function commonListParamsFromSearch(searchParams: URLSearchParams): ListParams {
  const category = normalizeCategorySelection(
    firstSearchParam(searchParams, 'cate1', 'category1'),
    firstSearchParam(searchParams, 'cate2', 'category2', 'categoryId'),
  );
  return {
    keyword: firstSearchParam(searchParams, 'keyword') || undefined,
    ...category,
    sort: firstSearchParam(searchParams, 'sort') || 'latest',
    order: normalizeOrder(firstSearchParam(searchParams, 'order', 'direction')),
    page: positiveNumber(firstSearchParam(searchParams, 'page'), 1),
    pageSize: positiveNumber(firstSearchParam(searchParams, 'pageSize', 'size'), 5),
  };
}

function firstSearchParam(searchParams: URLSearchParams, ...keys: string[]) {
  for (const key of keys) {
    const value = searchParams.get(key)?.trim();
    if (value) return value;
  }
  return '';
}

function positiveNumber(value: string, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function append(searchParams: URLSearchParams, key: string, value?: string) {
  if (value) searchParams.set(key, value);
}

function normalizeOrder(value: string) {
  return value.toLowerCase() === 'asc' ? 'asc' : 'desc';
}
