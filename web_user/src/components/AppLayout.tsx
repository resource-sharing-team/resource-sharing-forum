import {
  BookOutlined,
  CloudUploadOutlined,
  CompassOutlined,
  HomeOutlined,
  LoginOutlined,
  PlusOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import { useEffect } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useMe } from '../api/hooks';
import { useAuthStore } from '../store/auth';

const navItems = [
  { key: '/', icon: <HomeOutlined />, label: <Link to="/">首页</Link> },
  { key: '/resources', icon: <BookOutlined />, label: <Link to="/resources">资源库</Link> },
  { key: '/demands', icon: <CompassOutlined />, label: <Link to="/demands">求资源</Link> },
  { key: '/profile', icon: <UserOutlined />, label: <Link to="/profile">个人中心</Link> },
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
    <Layout className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/">
          <span className="brand-mark">
            <BookOutlined />
          </span>
          <span className="brand-name">
            资源分享论坛
            <span className="brand-subtitle">User Web Console</span>
          </span>
        </Link>

        <Menu
          className="topbar-menu"
          mode="horizontal"
          selectedKeys={[selectedKey(location.pathname)]}
          items={navItems}
        />

        <div className="topbar-actions">
          <Button icon={<CloudUploadOutlined />} onClick={() => navigate('/publish-resource')}>
            发布资源
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/publish-demand')}>
            发布求资源
          </Button>
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
              <Space style={{ cursor: 'pointer' }}>
                <Avatar src={activeUser.avatar} />
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
      <main className="page">
        <Outlet />
      </main>
    </Layout>
  );
}
