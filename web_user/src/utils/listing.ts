import type { Demand, ListParams, PagedResult, Resource } from '../types';

export function paginate<T>(items: T[], page = 1, pageSize = 10): PagedResult<T> {
  const safePage = Math.max(1, page);
  const safePageSize = Math.max(1, pageSize);
  const start = (safePage - 1) * safePageSize;

  return {
    items: items.slice(start, start + safePageSize),
    total: items.length,
    page: safePage,
    pageSize: safePageSize,
  };
}

export function filterResources(resources: Resource[], params: ListParams) {
  const keyword = params.keyword?.trim().toLowerCase();
  let result = [...resources];

  if (keyword) {
    result = result.filter((item) =>
      [item.title, item.description, item.author, item.type, ...item.tags]
        .join(' ')
        .toLowerCase()
        .includes(keyword),
    );
  }
  if (params.cate1) result = result.filter((item) => item.category1 === params.cate1);
  if (params.cate2) result = result.filter((item) => item.category2 === params.cate2);
  if (params.type) result = result.filter((item) => item.type === params.type);

  if (params.sort === 'download') {
    result.sort((a, b) => b.downloads - a.downloads);
  } else if (params.sort === 'score') {
    result.sort((a, b) => b.score - a.score);
  } else {
    result.sort((a, b) => b.date.localeCompare(a.date));
  }

  return result;
}

export function filterDemands(demands: Demand[], params: ListParams) {
  const keyword = params.keyword?.trim().toLowerCase();
  let result = [...demands];

  if (keyword) {
    result = result.filter((item) =>
      [item.title, item.description, item.author, item.format, ...item.tags]
        .join(' ')
        .toLowerCase()
        .includes(keyword),
    );
  }
  if (params.cate1) result = result.filter((item) => item.category1 === params.cate1);
  if (params.cate2) result = result.filter((item) => item.category2 === params.cate2);
  if (params.status) result = result.filter((item) => item.status === params.status);

  if (params.sort === 'points') {
    result.sort((a, b) => b.points - a.points);
  } else if (params.sort === 'reply') {
    result.sort((a, b) => b.replyCount - a.replyCount);
  } else {
    result.sort((a, b) => b.date.localeCompare(a.date));
  }

  return result;
}
