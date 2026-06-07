import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import App from './App';

describe('Admin app shell', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('renders the prototype-aligned admin navigation and default content page', () => {
    render(<App />);

    expect(screen.getByText('管理员管理系统')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '内容综合管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '用户账号管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '举报版权投诉' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '分类标签管理' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '系统参数配置' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '操作审计日志' })).toBeInTheDocument();
    expect(screen.getByText('资源审核列表')).toBeInTheDocument();
    expect(screen.getByText('驳回操作需填写原因，审核通过后前台正常展示')).toBeInTheDocument();
  });

  it('renders the login page when opened on /login', () => {
    window.history.pushState({}, '', '/login');

    render(<App />);

    expect(screen.getByRole('heading', { name: '资源分享论坛' })).toBeInTheDocument();
    expect(screen.getByText('后台管理系统')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登录后台' })).toBeInTheDocument();
  });

  it('keeps table actions available for classroom demonstration', () => {
    render(<App />);

    const resourceRow = screen.getByText('UI设计全套模板').closest('tr');
    expect(resourceRow).not.toBeNull();
    expect(within(resourceRow as HTMLTableRowElement).getByText('R001')).toBeInTheDocument();
    expect(within(resourceRow as HTMLTableRowElement).getByText('user001')).toBeInTheDocument();
    expect(within(resourceRow as HTMLTableRowElement).getByRole('button', { name: '通过审核' })).toBeInTheDocument();
    expect(within(resourceRow as HTMLTableRowElement).getByRole('button', { name: '驳回' })).toBeInTheDocument();
  });

  it('updates audit and user rows during mock review workflows', () => {
    render(<App />);

    const approvedRow = screen.getByText('UI设计全套模板').closest('tr') as HTMLTableRowElement;
    fireEvent.click(within(approvedRow).getByRole('button', { name: '通过审核' }));
    expect(within(approvedRow).getByText('已通过')).toBeInTheDocument();

    const rejectedRow = screen.getByText('办公表格合集').closest('tr') as HTMLTableRowElement;
    fireEvent.click(within(rejectedRow).getByRole('button', { name: '驳回' }));
    fireEvent.change(screen.getByPlaceholderText('请输入驳回缘由'), { target: { value: '资料说明不完整' } });
    fireEvent.click(screen.getByRole('button', { name: '确认驳回' }));
    expect(within(rejectedRow).getByText('已驳回')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: '用户账号管理' }));
    const userRow = screen.getByText('清风徐来').closest('tr') as HTMLTableRowElement;
    expect(within(userRow).getByText('U001')).toBeInTheDocument();
    fireEvent.click(within(userRow).getByRole('button', { name: '禁用账号' }));
    fireEvent.change(screen.getByPlaceholderText('请输入账号禁用缘由'), { target: { value: '多次发布违规资源' } });
    fireEvent.click(screen.getByRole('button', { name: '确认禁用' }));
    expect(within(userRow).getByText('已禁用')).toBeInTheDocument();
    expect(within(userRow).getByRole('button', { name: '恢复账号' })).toBeInTheDocument();
  });
});
