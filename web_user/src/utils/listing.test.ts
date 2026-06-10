import { describe, expect, it } from 'vitest';
import { resources, demands } from '../data/mockRecords';
import { filterDemands, filterResources, paginate } from './listing';

describe('listing utilities', () => {
  it('filters resources by keyword and category', () => {
    const result = filterResources(resources, { keyword: 'figma', cate1: '2' });
    expect(result).toHaveLength(1);
    expect(result[0].title).toContain('Figma');
  });

  it('sorts resources by downloads', () => {
    const result = filterResources(resources, { sort: 'download' });
    expect(result[0].downloads).toBeGreaterThanOrEqual(result[1].downloads);
  });

  it('filters demands by status and reward points', () => {
    const result = filterDemands(demands, { status: 'active', sort: 'points' });
    expect(result.every((item) => item.status === 'active')).toBe(true);
    expect(result[0].points).toBeGreaterThanOrEqual(result[1].points);
  });

  it('paginates with safe defaults', () => {
    const result = paginate(resources, -1, 2);
    expect(result.page).toBe(1);
    expect(result.items).toHaveLength(2);
  });
});
