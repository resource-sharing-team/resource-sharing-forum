import { describe, expect, it } from 'vitest';
import { normalizeApiPayload } from './client';

describe('api client response normalization', () => {
  it('unwraps backend response wrapper', () => {
    expect(
      normalizeApiPayload({
        code: 200,
        message: 'success',
        data: { token: 'jwt-token' },
        timestamp: '2026-06-06T00:00:00Z',
      }),
    ).toEqual({ token: 'jwt-token' });
  });

  it('normalizes backend pagination to frontend page shape', () => {
    expect(
      normalizeApiPayload({
        code: 200,
        message: 'success',
        data: {
          total: 1,
          list: [{ id: 1, title: 'Resource' }],
          page: 1,
          size: 6,
        },
        timestamp: '2026-06-06T00:00:00Z',
      }),
    ).toEqual({
      total: 1,
      list: [{ id: 1, title: 'Resource' }],
      items: [{ id: 1, title: 'Resource' }],
      page: 1,
      size: 6,
      pageSize: 6,
    });
  });
});
