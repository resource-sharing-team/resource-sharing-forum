import {
  BellOutlined,
  DeleteOutlined,
  GiftOutlined,
  HeartOutlined,
  LikeOutlined,
  LockOutlined,
  LoginOutlined,
  LogoutOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { message } from 'antd';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  useBindEmail,
  useCancelDemand,
  useChangePassword,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useMe,
  useNotifications,
  useResourceAction,
  useUpdateMe,
  useUserFavorites,
  useUserLikes,
  useUserLoginRecords,
  useUserRequests,
  useUserResources,
} from '../api/hooks';
import { ApiError } from '../components/ApiState';
import { useAuthStore } from '../store/auth';
import type { Demand, Resource } from '../types';
import { demandStatusLabel } from '../utils/format';

type TabKey = 'profile' | 'my-resource' | 'my-demand' | 'my-fav' | 'my-like' | 'member' | 'message' | 'security' | 'login-log';

const menu: Array<{ key: TabKey; label: string; icon: ReactNode }> = [
  { key: 'profile', label: '个人资料', icon: <UserOutlined /> },
  { key: 'my-resource', label: '我发布的资源', icon: <GiftOutlined /> },
  { key: 'my-demand', label: '我的求资源', icon: <MessageOutlined /> },
  { key: 'my-fav', label: '我的收藏', icon: <HeartOutlined /> },
  { key: 'my-like', label: '我的点赞', icon: <LikeOutlined /> },
  { key: 'member', label: '会员中心', icon: <SafetyCertificateOutlined /> },
  { key: 'message', label: '消息中心', icon: <BellOutlined /> },
  { key: 'security', label: '安全中心', icon: <LockOutlined /> },
  { key: 'login-log', label: '登录记录', icon: <LoginOutlined /> },
];

const tabKeys = new Set<TabKey>(menu.map((item) => item.key));

function getTabKey(value: string | null): TabKey {
  return value && tabKeys.has(value as TabKey) ? (value as TabKey) : 'profile';
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const active = getTabKey(searchParams.get('tab'));
  const meQuery = useMe();
  const updateMe = useUpdateMe();
  const changePassword = useChangePassword();
  const bindEmail = useBindEmail();
  const cancelDemand = useCancelDemand();
  const resourceAction = useResourceAction();
  const userResourcesQuery = useUserResources(active === 'my-resource');
  const userRequestsQuery = useUserRequests(active === 'my-demand');
  const userFavoritesQuery = useUserFavorites(active === 'my-fav');
  const userLikesQuery = useUserLikes(active === 'my-like');
  const notificationsQuery = useNotifications(true);
  const markNotificationRead = useMarkNotificationRead();
  const markAllNotificationsRead = useMarkAllNotificationsRead();
  const loginRecordsQuery = useUserLoginRecords(active === 'login-log');
  const { setUser, logout } = useAuthStore();
  const user = meQuery.data;
  const unreadCount = notificationsQuery.data?.items.filter((item) => item.unread).length || 0;

  const [profile, setProfile] = useState({ nickname: '', bio: '', avatar: '' });
  const [password, setPassword] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [email, setEmail] = useState({ email: '' });

  useEffect(() => {
    if (user) {
      setProfile({ nickname: user.nickname, bio: user.bio, avatar: user.avatar });
      setEmail((prev) => ({ ...prev, email: user.email }));
    }
  }, [user]);

  const percent = useMemo(() => {
    if (!user) return 0;
    return Math.min(100, Math.round((user.points / Math.max(user.expNeeded, 1)) * 100));
  }, [user]);

  if (meQuery.error) {
    return <div className="container"><div className="card"><div className="card-body"><ApiError error={meQuery.error} /></div></div></div>;
  }

  if (!user) {
    return <div className="container"><div className="card"><div className="card-body">加载中...</div></div></div>;
  }

  async function saveProfile() {
    if (profile.nickname.trim().length < 2) {
      message.warning('昵称需 2-20 个字符');
      return;
    }
    try {
      const next = await updateMe.mutateAsync(profile);
      setUser(next);
      message.success('保存成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function submitPassword() {
    if (password.newPassword.length < 8) {
      message.warning('新密码至少 8 位');
      return;
    }
    if (password.newPassword !== password.confirmPassword) {
      message.warning('两次密码不一致');
      return;
    }
    try {
      await changePassword.mutateAsync({ oldPassword: password.oldPassword, newPassword: password.newPassword });
      setPassword({ oldPassword: '', newPassword: '', confirmPassword: '' });
      message.success('密码修改成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function submitEmail() {
    try {
      const next = await bindEmail.mutateAsync({ email: email.email });
      setUser(next);
      message.success('邮箱更换成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function removeDemand(item: Demand) {
    if (!window.confirm(`确认取消求资源「${item.title}」吗？`)) return;
    try {
      await cancelDemand.mutateAsync(item.id);
      message.success('已取消该求资源请求');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function cancelFavorite(item: Resource) {
    try {
      await resourceAction.mutateAsync({ id: item.id, action: 'favorite' });
      message.success('已取消收藏');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function cancelLike(item: Resource) {
    try {
      await resourceAction.mutateAsync({ id: item.id, action: 'like' });
      message.success('已取消点赞');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  function switchTab(tab: TabKey) {
    const next = new URLSearchParams(searchParams);
    if (tab === 'profile') {
      next.delete('tab');
    } else {
      next.set('tab', tab);
    }
    setSearchParams(next);
  }

  return (
    <div className="container">
      <div className="main-wrapper">
        <aside className="left-menu">
          <div className="card">
            <div className="card-body">
              <div className="user-info">
                {user.avatar ? <img className="user-avatar" src={user.avatar} alt="头像" /> : <div className="user-avatar" />}
                <div className="user-text">
                  <h3>{user.nickname}</h3>
                  <p>{user.level} | 积分：{user.points}</p>
                  <div className="level-box">
                    <div className="level-text">
                      <span>升级进度</span>
                      <span>{user.points}/{user.expNeeded}</span>
                    </div>
                    <div className="progress-bar">
                      <div className="progress" style={{ width: `${percent}%` }} />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {menu.map((item) => (
            <div className={`menu-item ${active === item.key ? 'active' : ''}`} key={item.key} onClick={() => switchTab(item.key)}>
              <span className="menu-icon">{item.icon}</span>
              <span className="menu-text">{item.label}</span>
              {item.key === 'message' && unreadCount > 0 && <span className="menu-badge">{unreadCount}</span>}
            </div>
          ))}
          <div
            className="menu-item"
            style={{ color: '#f44336' }}
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            <span className="menu-icon"><LogoutOutlined /></span>
            <span className="menu-text">退出登录</span>
          </div>
        </aside>

        <main className="right-content">
          {active === 'profile' && (
            <div className="card">
              <div className="card-title">个人资料</div>
              <div className="card-body">
                <div className="avatar-preview">
                  {profile.avatar ? <img className="avatar-preview-img" src={profile.avatar} alt="头像" /> : <div className="avatar-preview-img" />}
                  <div>
                    <input className="form-input" value={profile.avatar} onChange={(event) => setProfile((prev) => ({ ...prev, avatar: event.target.value }))} placeholder="头像地址" />
                    <div className="tip">当前仅支持图片 URL；本地头像上传需后端提供头像上传接口</div>
                  </div>
                </div>
                <div className="form-item">
                  <label className="form-label">用户名</label>
                  <input className="form-input" value={user.username} readOnly />
                </div>
                <div className="form-item">
                  <label className="form-label">昵称</label>
                  <input className="form-input" value={profile.nickname} onChange={(event) => setProfile((prev) => ({ ...prev, nickname: event.target.value }))} maxLength={20} />
                </div>
                <div className="form-item">
                  <label className="form-label">简介</label>
                  <textarea className="form-textarea" value={profile.bio} onChange={(event) => setProfile((prev) => ({ ...prev, bio: event.target.value }))} maxLength={100} />
                </div>
                <button className="btn-primary" onClick={saveProfile} disabled={updateMe.isPending}>保存修改</button>
              </div>
            </div>
          )}

          {active === 'my-resource' && <ListCard title="我发布的资源" items={userResourcesQuery.data?.items || []} loading={userResourcesQuery.isLoading} error={userResourcesQuery.error} getHref={(item: Resource) => `/resources/${item.id}`} render={(item: Resource) => item.title} meta={(item: Resource) => `${item.date} | 状态：${resourceStatusLabel(item.status)} | 下载：${item.downloads}`} />}
          {active === 'my-demand' && <ListCard title="我的求资源" items={userRequestsQuery.data?.items || []} loading={userRequestsQuery.isLoading} error={userRequestsQuery.error} getHref={(item: Demand) => `/demands/${item.id}`} render={(item: Demand) => item.title} meta={(item: Demand) => `悬赏：${item.points}积分 | 状态：${demandStatusLabel(item.status)} | 回复：${item.replyCount}`} action={{ label: '取消', icon: <DeleteOutlined />, onClick: removeDemand, pending: cancelDemand.isPending }} />}
          {active === 'my-fav' && <ListCard title="我的收藏" items={userFavoritesQuery.data?.items || []} loading={userFavoritesQuery.isLoading} error={userFavoritesQuery.error} getHref={(item: Resource) => `/resources/${item.id}`} render={(item: Resource) => item.title} meta={(item: Resource) => `${item.date} | ${item.type}`} action={{ label: '取消收藏', onClick: cancelFavorite, pending: resourceAction.isPending }} />}
          {active === 'my-like' && <ListCard title="我的点赞" items={userLikesQuery.data?.items || []} loading={userLikesQuery.isLoading} error={userLikesQuery.error} getHref={(item: Resource) => `/resources/${item.id}`} render={(item: Resource) => item.title} meta={(item: Resource) => `${item.date} | ${item.author}`} action={{ label: '取消点赞', onClick: cancelLike, pending: resourceAction.isPending }} />}
          {active === 'member' && <MemberCenter points={user.points} level={user.level} expNeeded={user.expNeeded} percent={percent} />}
          {active === 'message' && <MessageCenter messages={notificationsQuery.data?.items || []} loading={notificationsQuery.isLoading} error={notificationsQuery.error} onRead={(id) => markNotificationRead.mutateAsync(id)} onReadAll={() => markAllNotificationsRead.mutateAsync()} pending={markNotificationRead.isPending || markAllNotificationsRead.isPending} />}
          {active === 'security' && (
            <>
              <div className="card">
                <div className="card-title">修改密码</div>
                <div className="card-body">
                  <div className="form-item"><label className="form-label">当前密码</label><input className="form-input" type="password" value={password.oldPassword} onChange={(event) => setPassword((prev) => ({ ...prev, oldPassword: event.target.value }))} /></div>
                  <div className="form-item"><label className="form-label">新密码</label><input className="form-input" type="password" value={password.newPassword} onChange={(event) => setPassword((prev) => ({ ...prev, newPassword: event.target.value }))} /><div className="tip">8-20位，包含字母和数字</div></div>
                  <div className="form-item"><label className="form-label">确认新密码</label><input className="form-input" type="password" value={password.confirmPassword} onChange={(event) => setPassword((prev) => ({ ...prev, confirmPassword: event.target.value }))} /></div>
                  <button className="btn-primary" onClick={submitPassword} disabled={changePassword.isPending}>修改密码</button>
                </div>
              </div>
              <div className="card">
                <div className="card-title">更换邮箱</div>
                <div className="card-body">
                  <div className="form-item"><label className="form-label">当前邮箱</label><input className="form-input" value={user.email} readOnly /></div>
                  <div className="form-item"><label className="form-label">新邮箱</label><input className="form-input" value={email.email} onChange={(event) => setEmail({ email: event.target.value })} /></div>
                  <button className="btn-primary" onClick={submitEmail} disabled={bindEmail.isPending}>确认更换</button>
                </div>
              </div>
            </>
          )}
          {active === 'login-log' && (
            <div className="card">
              <div className="card-title">最近登录记录</div>
              <div className="card-body">
                <table className="login-table">
                  <thead><tr><th>登录时间</th><th>IP地址</th><th>设备/地理位置</th></tr></thead>
                  <tbody>
                    {(loginRecordsQuery.data?.items || []).map((log) => (
                      <tr key={log.id}><td>{log.time}</td><td>{log.ip}</td><td>{log.device} {log.location}</td></tr>
                    ))}
                  </tbody>
                </table>
                {loginRecordsQuery.isLoading && <div className="tip" style={{ marginTop: 12 }}>加载中...</div>}
                {loginRecordsQuery.error ? <ApiError error={loginRecordsQuery.error} /> : null}
                {!loginRecordsQuery.isLoading && !loginRecordsQuery.error && !(loginRecordsQuery.data?.items || []).length && <div className="tip" style={{ marginTop: 12 }}>暂无登录记录</div>}
                <div className="tip" style={{ marginTop: 12 }}>如发现异常登录，请及时修改密码</div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

function resourceStatusLabel(status?: string) {
  const normalized = (status || '').toUpperCase();
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '待审核',
    REVIEWING_RISK: '风险复核中',
    PUBLISHED: '已发布',
    REJECTED: '审核未通过',
    OFFLINE: '已下架',
    COPYRIGHT_DOWN: '版权下架',
    DELETED: '已删除',
  };
  return labels[normalized] || status || '未知';
}

type ListCardAction<T> = {
  label: string;
  icon?: ReactNode;
  pending?: boolean;
  onClick: (item: T) => void | Promise<void>;
};

function ListCard<T extends { id: number }>({
  title,
  items,
  loading,
  error,
  getHref,
  render,
  meta,
  action,
}: {
  title: string;
  items: T[];
  loading?: boolean;
  error?: unknown;
  getHref: (item: T) => string;
  render: (item: T) => ReactNode;
  meta: (item: T) => ReactNode;
  action?: ListCardAction<T>;
}) {
  const navigate = useNavigate();

  function openItem(item: T) {
    navigate(getHref(item));
  }

  return (
    <div className="card">
      <div className="card-title">{title}</div>
      <div className="card-body">
        {loading && <div className="tip" style={{ textAlign: 'center', padding: 20 }}>加载中...</div>}
        {error ? <ApiError error={error} /> : null}
        {items.map((item) => (
          <div
            className="list-item clickable"
            key={item.id}
            onClick={(event) => {
              if ((event.target as HTMLElement).closest('button')) return;
              openItem(item);
            }}
          >
            <div className="list-main">
              <div className="item-title">{render(item)}</div>
              <div className="item-meta">{meta(item)}</div>
            </div>
            {action && (
              <button
                className="item-btn btn-delete list-action"
                disabled={action.pending}
                onMouseDown={(event) => event.stopPropagation()}
                onClick={(event) => {
                  event.stopPropagation();
                  void action.onClick(item);
                }}
              >
                {action.icon}
                {action.label}
              </button>
            )}
          </div>
        ))}
        {!loading && !error && !items.length && <div className="tip" style={{ textAlign: 'center', padding: 20 }}>暂无数据</div>}
      </div>
    </div>
  );
}

function MemberCenter({ points, level, expNeeded, percent }: { points: number; level: string; expNeeded: number; percent: number }) {
  return (
    <div className="card">
      <div className="card-title">会员中心</div>
      <div className="card-body">
        <div className="member-current-card">
          <div className="member-hd"><div className="member-level-name">{level}</div><div className="member-points">当前积分：{points} 分</div></div>
          <div className="member-progress-section">
            <div className="progress-text"><span>距离升级：活跃会员</span><span>{points} / {expNeeded}</span></div>
            <div className="progress-bar-bg"><div className="progress-bar-active" style={{ width: `${percent}%` }} /></div>
            <div className="progress-tip">距离升级还差 {Math.max(0, expNeeded - points)} 积分</div>
          </div>
          <div className="member-stats"><div><span>可用积分</span><strong>{points}</strong></div><div><span>冻结积分</span><strong>0</strong></div></div>
        </div>
        <div className="member-section"><div className="member-section-title">我的当前权益</div><div className="member-benefits"><div>• 每日下载次数：10 次</div><div>• 单资源最大附件：5 个</div><div>• 单文件最大大小：100MB</div><div>• 悬赏积分上限：100 分</div><div>• 资源置顶资格：无</div></div></div>
        <div className="member-section"><div className="member-section-title">会员等级规则</div><div className="member-level-table"><div className="member-level-item"><span>普通会员</span><span>0 ~ 99 积分</span></div><div className="member-level-item"><span>活跃会员</span><span>100 ~ 499 积分</span></div><div className="member-level-item"><span>优质会员</span><span>500 ~ 1999 积分</span></div><div className="member-level-item"><span>资深会员</span><span>≥2000 积分</span></div></div></div>
        <div className="member-section"><div className="member-section-title">全等级权益对照表</div><div className="member-benefit-table"><div className="benefit-head"><div>权益项</div><div>普通会员</div><div>活跃会员</div><div>优质会员</div><div>资深会员</div></div><div className="benefit-row"><div>每日下载次数</div><div>10</div><div>20</div><div>50</div><div>100</div></div><div className="benefit-row"><div>单资源最大附件</div><div>5</div><div>8</div><div>10</div><div>15</div></div><div className="benefit-row"><div>单文件最大大小</div><div>100MB</div><div>100MB</div><div>100MB</div><div>200MB</div></div><div className="benefit-row"><div>资源置顶资格</div><div>无</div><div>无</div><div>有</div><div>有</div></div><div className="benefit-row"><div>悬赏积分上限</div><div>100</div><div>500</div><div>2000</div><div>10000</div></div></div></div>
        <div className="member-rule-group"><div className="member-section-title">积分获取规则</div><div className="member-rule"><div>• 资源审核通过 + 积分</div><div>• 资源被他人下载 + 积分</div><div>• 资源被收藏 + 积分</div><div>• 评论被点赞 + 积分</div><div>• 求资源回答被采纳 + 奖励积分</div></div></div>
        <div className="member-rule-group"><div className="member-section-title">积分扣减规则</div><div className="member-rule deduct"><div>• 资源违规下架 - 扣除积分</div><div>• 举报/版权投诉成立 - 扣除积分</div><div>• 评论违规删除 - 扣除积分</div><div>• 求资源违规关闭 - 扣除积分</div></div></div>
      </div>
    </div>
  );
}

function MessageCenter({
  messages,
  loading,
  error,
  pending,
  onRead,
  onReadAll,
}: {
  messages: Array<{ id: number; title: string; content: string; unread: boolean; date: string }>;
  loading?: boolean;
  error?: unknown;
  pending?: boolean;
  onRead: (id: number) => Promise<unknown>;
  onReadAll: () => Promise<unknown>;
}) {
  const [localMessages, setLocalMessages] = useState(messages);

  useEffect(() => {
    setLocalMessages(messages);
  }, [messages]);

  async function markAllRead() {
    try {
      await onReadAll();
      setLocalMessages((prev) => prev.map((item) => ({ ...item, unread: false })));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function markRead(id: number) {
    try {
      await onRead(id);
      setLocalMessages((prev) => prev.map((messageItem) => (messageItem.id === id ? { ...messageItem, unread: false } : messageItem)));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  return (
    <div className="card">
      <div className="card-title">消息中心</div>
      <div className="card-body">
        <div className="msg-all-read"><button onClick={markAllRead} disabled={pending || Boolean(error)}>全部标为已读</button></div>
        {loading && <div className="tip" style={{ textAlign: 'center', padding: 20 }}>加载中...</div>}
        {error ? <ApiError error={error} /> : null}
        {localMessages.map((item) => (
          <div className={`msg-item ${item.unread ? 'msg-unread' : ''}`} key={item.id} onClick={() => markRead(item.id)}>
            <div className="msg-title">{item.title}</div>
            <div className="msg-content">{item.content}</div>
            <div className="msg-time">{item.date}</div>
          </div>
        ))}
        {!loading && !error && !localMessages.length && <div className="tip" style={{ textAlign: 'center', padding: 20 }}>暂无消息</div>}
      </div>
    </div>
  );
}
