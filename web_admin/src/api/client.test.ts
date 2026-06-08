import { describe, expect, it } from 'vitest';
import { normalizeApiPayload } from './client';

describe('normalizeApiPayload', () => {
  it('unwraps backend ApiResponse envelopes', () => {
    const result = normalizeApiPayload({
      code: 200,
      message: 'success',
      data: { id: 1, title: '资源审核' },
      timestamp: '2026-06-06T12:00:00+08:00',
    });

    expect(result).toEqual({ id: 1, title: '资源审核' });
  });

  it('normalizes backend pagination list and size fields', () => {
    const result = normalizeApiPayload({
      code: 200,
      data: {
        total: 2,
        list: [{ id: 1 }, { id: 2 }],
        page: 1,
        size: 20,
      },
    });

    expect(result).toEqual({
      total: 2,
      list: [{ id: 1 }, { id: 2 }],
      items: [{ id: 1 }, { id: 2 }],
      page: 1,
      size: 20,
      pageSize: 20,
    });
  });
});
