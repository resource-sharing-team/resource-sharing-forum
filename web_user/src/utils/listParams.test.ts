import { describe, expect, it } from 'vitest';
import { normalizeCategorySelection } from '../data/catalog';
import { demandListParamsFromSearch, listParamsToSearchParams, resourceListParamsFromSearch } from './listParams';

describe('list param normalization', () => {
  it('infers first-level category from second-level category', () => {
    expect(normalizeCategorySelection(undefined, '22')).toEqual({ cate1: '2', cate2: '22' });
  });

  it('ignores a second-level category outside the selected first-level category', () => {
    expect(normalizeCategorySelection('1', '22')).toEqual({ cate1: '1', cate2: undefined });
  });

  it('supports resource category aliases used by backend and homepage links', () => {
    const params = resourceListParamsFromSearch(new URLSearchParams('categoryId=41&resourceType=教程&sort=score&page=2&pageSize=8'));
    expect(params).toMatchObject({ cate1: '4', cate2: '41', type: '教程', sort: 'score', page: 2, pageSize: 8 });
  });

  it('serializes demand filters with normalized category keys', () => {
    const params = demandListParamsFromSearch(new URLSearchParams('cate2=21&status=active&points=100-500&sort=points&order=asc'));
    expect(listParamsToSearchParams(params, 'demands').toString()).toBe('cate1=2&cate2=21&status=active&points=100-500&sort=points&order=asc');
  });

  it('keeps demand sort direction explicit for refreshable URLs', () => {
    expect(listParamsToSearchParams({ page: 1, pageSize: 5, sort: 'points', order: 'desc' }, 'demands').toString()).toBe('sort=points&order=desc');
    expect(demandListParamsFromSearch(new URLSearchParams('sort=points&order=sideways'))).toMatchObject({ sort: 'points', order: 'desc' });
  });
});
