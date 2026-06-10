import type { AxiosError } from 'axios';
import { describe, expect, it } from 'vitest';
import { getErrorMessage } from './client';

function axiosError(overrides: Partial<AxiosError>): AxiosError {
  return {
    name: 'AxiosError',
    message: 'Request failed',
    isAxiosError: true,
    config: {
      method: 'get',
      url: '/categories',
      headers: {} as never,
    },
    toJSON: () => ({}),
    ...overrides,
  } as AxiosError;
}

describe('getErrorMessage', () => {
  it('includes endpoint and status when backend returns a generic message', () => {
    const message = getErrorMessage(
      axiosError({
        response: {
          status: 500,
          statusText: 'Internal Server Error',
          data: {
            code: 500,
            message: '系统繁忙，请稍后再试',
            data: null,
          },
        } as AxiosError['response'],
      }),
    );

    expect(message).toContain('GET /api/categories');
    expect(message).toContain('HTTP 500');
    expect(message).toContain('后端内部错误');
    expect(message).toContain('系统繁忙，请稍后再试');
  });

  it('includes endpoint details for network failures', () => {
    const message = getErrorMessage(
      axiosError({
        code: 'ERR_NETWORK',
        message: 'Network Error',
        config: {
          method: 'get',
          url: '/resource-types',
          headers: {} as never,
        },
      }),
    );

    expect(message).toContain('GET /api/resource-types');
    expect(message).toContain('网络错误，后端无法访问');
  });
});
