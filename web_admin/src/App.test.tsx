import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ComponentType } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let App: ComponentType;

const ADMIN_AUTH_KEY = 'resource-forum-admin-auth';

const login = () => {
  localStorage.setItem(
    ADMIN_AUTH_KEY,
    JSON.stringify({
      state: {
        token: 'test-admin-token',
        role: 'ADMIN',
        user: { id: 2, username: 'admin', nickname: '审核管理员', role: 'ADMIN' },
      },
    }),
  );
};

describe('Admin app shell', () => {
  beforeEach(async () => {
    localStorage.clear();
    window.history.pushState({}, '', '/');
    vi.unstubAllEnvs();
    vi.stubEnv('VITE_ENABLE_MOCKS', 'true');
    vi.resetModules();
    App = (await import('./App')).default;
  });

  it('logs in and logs out through the admin shell', async () => {
    window.history.pushState({}, '', '/login');
    render(<App />);

    expect(screen.getByRole('heading', { name: '资源分享论坛' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '登录后台' }));

    expect(await screen.findByText('管理员管理系统')).toBeInTheDocument();
    expect(await screen.findByText('资源审核列表')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '退出系统' }));
    expect(screen.getByRole('button', { name: '登录后台' })).toBeInTheDocument();
  });

  it('renders the prototype-aligned admin navigation and default content page', async () => {
    login();
    render(<App />);

    expect(await screen.findByText('管理员管理系统')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '内容综合管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '用户账号管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '举报版权投诉' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '分类标签管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '系统参数配置' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '操作审计日志' })).toBeInTheDocument();
    expect(await screen.findByText('资源审核列表')).toBeInTheDocument();
    expect(screen.getByText('驳回操作需填写原因，审核通过后前台正常展示')).toBeInTheDocument();
  });

  it('clears expired persisted admin sessions before rendering protected pages', async () => {
    localStorage.setItem(
      ADMIN_AUTH_KEY,
      JSON.stringify({
        state: {
          token: 'expired-admin-token',
          role: 'ADMIN',
          expireAt: new Date(Date.now() - 60_000).toISOString(),
          user: { id: 2, username: 'admin', nickname: 'admin', role: 'ADMIN' },
        },
      }),
    );

    render(<App />);

    await waitFor(() => expect(document.querySelector('.btn-login')).toBeInTheDocument());
    expect(localStorage.getItem(ADMIN_AUTH_KEY)).toBeNull();
  });

  it('keeps prototype ids and removes meaningless page two for one-page tables', async () => {
    login();
    render(<App />);

    const resourceRow = (await screen.findByText('UI设计全套模板')).closest('tr');
    expect(resourceRow).not.toBeNull();
    expect(within(resourceRow as HTMLTableRowElement).getByText('R001')).toBeInTheDocument();
    expect(within(resourceRow as HTMLTableRowElement).getByText('user001')).toBeInTheDocument();
    expect(within(resourceRow as HTMLTableRowElement).getByRole('button', { name: '通过审核' })).toBeInTheDocument();

    const pagination = screen.getByLabelText('资源审核列表分页');
    expect(within(pagination).getByRole('button', { name: '1' })).toBeInTheDocument();
    expect(within(pagination).queryByRole('button', { name: '2' })).not.toBeInTheDocument();
  });

  it('persists mock review actions after a page refresh', async () => {
    login();
    const { unmount } = render(<App />);

    const approvedRow = (await screen.findByText('UI设计全套模板')).closest('tr') as HTMLTableRowElement;
    fireEvent.click(within(approvedRow).getByRole('button', { name: '通过审核' }));
    await waitFor(() => expect(within(approvedRow).getByText('已通过')).toBeInTheDocument());

    unmount();
    render(<App />);

    const refreshedRow = (await screen.findByText('UI设计全套模板')).closest('tr') as HTMLTableRowElement;
    expect(within(refreshedRow).getByText('已通过')).toBeInTheDocument();
  });

  it('updates audit and user rows during mock review workflows', async () => {
    login();
    render(<App />);

    const rejectedRow = (await screen.findByText('办公表格合集')).closest('tr') as HTMLTableRowElement;
    fireEvent.click(within(rejectedRow).getByRole('button', { name: '驳回' }));
    fireEvent.change(screen.getByPlaceholderText('请输入驳回缘由'), { target: { value: '资料说明不完整' } });
    fireEvent.click(screen.getByRole('button', { name: '确认驳回' }));
    await waitFor(() => expect(within(rejectedRow).getByText('已驳回')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('link', { name: '用户账号管理' }));
    const userRow = (await screen.findByText('清风徐来')).closest('tr') as HTMLTableRowElement;
    expect(within(userRow).getByText('U001')).toBeInTheDocument();
    fireEvent.click(within(userRow).getByRole('button', { name: '禁用账号' }));
    fireEvent.change(screen.getByPlaceholderText('请输入账号禁用缘由'), { target: { value: '多次发布违规资源' } });
    fireEvent.click(screen.getByRole('button', { name: '确认禁用' }));
    await waitFor(() => expect(within(userRow).getByText('已禁用')).toBeInTheDocument());
    expect(within(userRow).getByRole('button', { name: '恢复账号' })).toBeInTheDocument();
  });

  it('filters audit logs by selected operation type', async () => {
    login();
    window.history.pushState({}, '', '/logs');
    render(<App />);

    expect(await screen.findByText('RS20260418')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('操作类型'), { target: { value: 'ACCOUNT_LOGIN' } });
    fireEvent.click(screen.getByRole('button', { name: '查询' }));

    await waitFor(() => expect(screen.queryByText('RS20260418')).not.toBeInTheDocument());
    expect(screen.getAllByText('AD001')).toHaveLength(2);
    expect(screen.getByText('共 1 条记录')).toBeInTheDocument();
  });
});
