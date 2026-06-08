import { LoginOutlined } from '@ant-design/icons';
import { Avatar, Button, Dropdown, Space, Typography } from 'antd';
import { useEffect } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useMe } from '../api/hooks';
import { useAuthStore } from '../store/auth';

const navItems = [
  { to: '/', label: '首页' },
  { to: '/resources', label: '资源库' },
  { to: '/demands', label: '求资源' },
  { to: '/profile', label: '个人中心' },
];

function selectedKey(pathname: string) {
  if (pathname.startsWith('/resources')) return '/resources';
  if (pathname.startsWith('/demands')) return '/demands';
  if (pathname.startsWith('/profile')) return '/profile';
  return '/';
}

export default function AppLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, setUser, logout } = useAuthStore();
  const meQuery = useMe();
  const activeUser = user || meQuery.data;

  useEffect(() => {
    if (meQuery.data) setUser(meQuery.data);
  }, [meQuery.data, setUser]);

  return (
    <div className="app-shell">
      <header className="header">
        <Link className="logo" to="/">
          资源分享论坛
        </Link>

        <nav className="nav" aria-label="前台导航">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className={selectedKey(location.pathname) === item.to ? 'active' : undefined}>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="user-bar">
          {activeUser ? (
            <Dropdown
              menu={{
                items: [
                  { key: 'profile', label: '个人中心', onClick: () => navigate('/profile') },
                  {
                    key: 'logout',
                    label: '退出登录',
                    danger: true,
                    onClick: () => {
                      logout();
                      navigate('/login');
                    },
                  },
                ],
              }}
            >
              <Space className="user-trigger">
                <Avatar className="avatar" src={activeUser.avatar || undefined}>
                  {activeUser.nickname?.slice(0, 1)}
                </Avatar>
                <Typography.Text strong>{activeUser.nickname}</Typography.Text>
              </Space>
            </Dropdown>
          ) : (
            <Button icon={<LoginOutlined />} onClick={() => navigate('/login')}>
              登录
            </Button>
          )}
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  );
}
