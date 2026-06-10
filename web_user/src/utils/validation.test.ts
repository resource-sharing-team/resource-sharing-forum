import { describe, expect, it } from 'vitest';
import { demandPublishSchema, resourcePublishSchema } from './validation';

describe('publish validation', () => {
  it('accepts a valid resource publish payload', () => {
    const result = resourcePublishSchema.safeParse({
      title: 'React 项目完整学习资料',
      category1: '4',
      category2: '41',
      type: '教程',
      tags: ['React'],
      description: '这是一份适合课程学习和项目实践的 React 教程资料包。',
      detail: '包含项目源码、运行说明、接口 mock、组件拆分说明和部署指南，适合小组协作实现。',
    });
    expect(result.success).toBe(true);
  });

  it('rejects demands with negative points', () => {
    const result = demandPublishSchema.safeParse({
      title: '求 React 学习资料',
      category1: '4',
      category2: '41',
      tags: ['React'],
      description: '希望获得一份完整的 React 学习资料和项目源码说明。',
      format: 'PDF / 源码',
      points: -1,
    });
    expect(result.success).toBe(false);
  });
});
