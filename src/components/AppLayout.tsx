import { useEffect } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useMe } from '../api/hooks';
import { useAuthStore } from '../store/auth';

function selectedKey(pathname: string) {
  if (pathname.startsWith('/resources') || pathname.startsWith('/publish-resource')) return '/resources';
  if (pathname.startsWith('/demands') || pathname.startsWith('/publish-demand')) return '/demands';
  if (pathname.startsWith('/profile') || pathname.startsWith('/user-center')) return '/profile';
  return '/';
}

export default function AppLayout() {
  const location = useLocation();
  const { token, user, setUser } = useAuthStore();
  const meQuery = useMe(Boolean(token));
  const activeUser = user || meQuery.data;
  const active = selectedKey(location.pathname);

  useEffect(() => {
    if (meQuery.data) setUser(meQuery.data);
  }, [meQuery.data, setUser]);

  return (
    <div className="app-shell">
      <header className="header">
        <Link className="logo" to="/">
          资源分享论坛
        </Link>
        <nav className="nav">
          <Link className={active === '/' ? 'active' : undefined} to="/">
            首页
          </Link>
          <Link className={active === '/resources' ? 'active' : undefined} to="/resources">
            资源库
          </Link>
          <Link className={active === '/demands' ? 'active' : undefined} to="/demands">
            求资源
          </Link>
          <Link className={active === '/profile' ? 'active' : undefined} to="/profile">
            个人中心
          </Link>
        </nav>
        <Link className="user-bar" to={activeUser ? '/profile' : '/login'}>
          {activeUser?.avatar ? <img className="avatar" src={activeUser.avatar} alt="头像" /> : <div className="avatar" />}
          <span>{activeUser?.nickname || '未登录'}</span>
        </Link>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  );
}
