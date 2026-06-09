import { describe, expect, it } from 'vitest';
import { resources, demands } from '../data/mockRecords';
import { categories, resourceTypes } from '../data/catalog';
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

  it('filters demands by reward range and sorts ascending', () => {
    const result = filterDemands(demands, { points: '0-100', sort: 'points', order: 'asc' });
    expect(result.every((item) => item.points > 0 && item.points <= 100)).toBe(true);
    expect(result[0].points).toBeLessThanOrEqual(result[1].points);
  });

  it('paginates with safe defaults', () => {
    const result = paginate(resources, -1, 2);
    expect(result.page).toBe(1);
    expect(result.items).toHaveLength(2);
  });

  it('keeps demo tags aligned with category and resource type taxonomy', () => {
    const canonicalTags = new Set([
      ...categories.map((category) => category.name),
      ...categories.flatMap((category) => category.children.map((child) => child.name)),
      ...resourceTypes,
    ]);

    for (const resource of resources) {
      expect(resource.tags).toEqual([
        categories.find((category) => category.id === resource.category1)?.name,
        categories.find((category) => category.id === resource.category1)?.children.find((child) => child.id === resource.category2)?.name,
        resource.type,
      ]);
      expect(resource.tags.every((tag) => canonicalTags.has(tag))).toBe(true);
    }

    for (const demand of demands) {
      expect(demand.tags).toEqual([
        categories.find((category) => category.id === demand.category1)?.name,
        categories.find((category) => category.id === demand.category1)?.children.find((child) => child.id === demand.category2)?.name,
        demand.format,
      ]);
      expect(demand.tags.every((tag) => canonicalTags.has(tag))).toBe(true);
    }
  });
});
