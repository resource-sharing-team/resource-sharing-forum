import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import {
  categories,
  comments,
  complaints,
  logs,
  managedResources,
  memberLevels,
  pendingResources,
  reports,
  requestPosts,
  users,
} from './data/mockAdmin';
import type { AdminCategory, AdminComment, AdminComplaint, AdminLog, AdminReport, AdminRequestPost, AdminResource, AdminUser, MemberLevel } from './types';

const SESSION_KEY = 'rsf_admin_session';

const navItems = [
  { to: '/', label: '内容综合管理' },
  { to: '/users', label: '用户账号管理' },
  { to: '/reports', label: '举报版权投诉' },
  { to: '/categories', label: '分类标签管理' },
  { to: '/config', label: '系统参数配置' },
  { to: '/logs', label: '操作审计日志' },
];

type ModalState = null | {
  title: string;
  placeholder: string;
  confirmText: string;
  message: string;
  onConfirm?: (reason: string) => void;
};

type ConfigItem = {
  label: string;
  value: string;
};

function storedValue<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function useStoredState<T>(key: string, fallback: T) {
  const [value, setValue] = useState<T>(() => storedValue(key, fallback));

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue] as const;
}

export default function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(() => localStorage.getItem(SESSION_KEY) === 'active');

  const handleLogin = () => {
    localStorage.setItem(SESSION_KEY, 'active');
    setIsLoggedIn(true);
  };

  const handleLogout = () => {
    localStorage.removeItem(SESSION_KEY);
    setIsLoggedIn(false);
  };

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
        <Route path="/*" element={isLoggedIn ? <AdminShell onLogout={handleLogout} /> : <Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function LoginPage({ onLogin }: { onLogin: () => void }) {
  const navigate = useNavigate();
  const [account, setAccount] = useState('admin');
  const [password, setPassword] = useState('password');
  const [error, setError] = useState('');

  return (
    <main className="login-page">
      <section className="login-card" aria-label="后台登录">
        <div className="login-logo" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" />
            <path d="M2 17L12 22L22 17" />
            <path d="M2 12L12 17L22 12" />
            <circle cx="12" cy="12" r="2.2" />
          </svg>
        </div>
        <h1>资源分享论坛</h1>
        <div className="login-subtitle">后台管理系统</div>
        <form
          className="login-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (account.trim() === 'admin' && password === 'password') {
              setError('');
              onLogin();
              navigate('/', { replace: true });
              return;
            }
            setError('账号或密码错误，演示账号为 admin / password');
          }}
        >
          <label htmlFor="admin-account">
            管理员账号
            <input id="admin-account" type="text" value={account} onChange={(event) => setAccount(event.target.value)} />
          </label>
          <label htmlFor="admin-password">
            登录密码
            <input id="admin-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          {error && <div className="login-error">{error}</div>}
          <button type="submit" className="btn-login">
            登录后台
          </button>
        </form>
        <p className="login-footer">Resource Sharing Forum · 管理入口</p>
      </section>
    </main>
  );
}

function AdminShell({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate();
  const [modal, setModal] = useState<ModalState>(null);
  const [notice, setNotice] = useState('');
  const openModal = (nextModal: ModalState) => {
    setNotice('');
    setModal(nextModal);
  };

  return (
    <div className="admin-page">
      <header className="top">
        <h3>管理员管理系统</h3>
        <div className="top-account">
          <span>管理员账号</span>
          <button
            type="button"
            className="top-logout"
            onClick={() => {
              onLogout();
              navigate('/login', { replace: true });
            }}
          >
            退出系统
          </button>
        </div>
      </header>
      <div className="container">
        <aside className="left" aria-label="后台导航">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'}>
              {item.label}
            </NavLink>
          ))}
        </aside>
        <main className="main">
          {notice && <div className="notice">{notice}</div>}
          <Routes>
            <Route path="/" element={<ContentPage openModal={openModal} setNotice={setNotice} />} />
            <Route path="/users" element={<UsersPage openModal={openModal} setNotice={setNotice} />} />
            <Route path="/reports" element={<ReportsPage openModal={openModal} setNotice={setNotice} />} />
            <Route path="/categories" element={<CategoriesPage openModal={openModal} setNotice={setNotice} />} />
            <Route path="/config" element={<ConfigPage setNotice={setNotice} />} />
            <Route path="/logs" element={<LogsPage />} />
            <Route path="*" element={<ContentPage openModal={openModal} setNotice={setNotice} />} />
          </Routes>
        </main>
      </div>
      <ReasonModal
        modal={modal}
        onClose={() => setModal(null)}
        onConfirm={(message) => {
          setNotice(message);
          setModal(null);
        }}
      />
    </div>
  );
}

function ContentPage({
  openModal,
  setNotice,
}: {
  openModal: (modal: ModalState) => void;
  setNotice: (message: string) => void;
}) {
  const [activeTab, setActiveTab] = useState('audit');
  const [auditRows, setAuditRows] = useStoredState<AdminResource[]>('rsf_admin_audit_rows', pendingResources);
  const [resourceRows, setResourceRows] = useStoredState<AdminResource[]>('rsf_admin_resource_rows', managedResources);
  const [requestRows, setRequestRows] = useStoredState<AdminRequestPost[]>('rsf_admin_request_rows', requestPosts);
  const [commentRows, setCommentRows] = useStoredState<AdminComment[]>('rsf_admin_comment_rows', comments);
  const tabs = [
    { id: 'audit', label: '资源审核列表' },
    { id: 'status', label: '资源上下架管理' },
    { id: 'request', label: '求资源帖子管理' },
    { id: 'comment', label: '评论内容管理' },
  ];

  return (
    <>
      <TabHead tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />
      <section className="tab-content">
        {activeTab === 'audit' && (
          <Panel>
            <table>
              <thead>
                <tr>
                  <th>资源ID</th>
                  <th>资源名称</th>
                  <th>发布用户</th>
                  <th>当前状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {auditRows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.title}</td>
                    <td>{item.user}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === '待审核' ? (
                        <>
                          <button
                            className="btn pass"
                            onClick={() => {
                              setAuditRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已通过' } : row)));
                              setNotice(`${item.id} 已通过审核`);
                            }}
                          >
                            通过审核
                          </button>
                          <button
                            className="btn reject"
                            onClick={() =>
                              openModal({
                                title: '填写驳回原因',
                                placeholder: '请输入驳回缘由',
                                confirmText: '确认驳回',
                                message: `${item.id} 已驳回，原因已记录`,
                                onConfirm: () =>
                                  setAuditRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已驳回' } : row))),
                              })
                            }
                          >
                            驳回
                          </button>
                        </>
                      ) : (
                        <span className="muted">已处理</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination ariaLabel="资源审核列表分页" total={auditRows.length} />
            <p className="tip">驳回操作需填写原因，审核通过后前台正常展示</p>
          </Panel>
        )}

        {activeTab === 'status' && (
          <Panel>
            <table>
              <thead>
                <tr>
                  <th>资源ID</th>
                  <th>资源名称</th>
                  <th>当前状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {resourceRows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.title}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === '已发布' ? (
                        <button
                          className="btn off"
                          onClick={() => {
                            setResourceRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已下架' } : row)));
                            setNotice(`${item.id} 已下架`);
                          }}
                        >
                          下架
                        </button>
                      ) : item.status === '已下架' ? (
                        <button
                          className="btn restore"
                          onClick={() => {
                            setResourceRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已发布' } : row)));
                            setNotice(`${item.id} 已恢复上架`);
                          }}
                        >
                          恢复上架
                        </button>
                      ) : (
                        <span className="muted">不可操作</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination ariaLabel="资源上下架管理分页" total={resourceRows.length} />
            <p className="tip">下架资源前台隐藏，版权下架资源禁止恢复</p>
          </Panel>
        )}

        {activeTab === 'request' && (
          <Panel>
            <table>
              <thead>
                <tr>
                  <th>帖子ID</th>
                  <th>求助标题</th>
                  <th>当前状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {requestRows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.title}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === '已关闭' ? (
                        <button
                          className="btn restore"
                          onClick={() => {
                            setRequestRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '进行中' } : row)));
                            setNotice(`${item.id} 已恢复`);
                          }}
                        >
                          恢复帖子
                        </button>
                      ) : (
                        <button
                          className="btn del"
                          onClick={() => {
                            setRequestRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已关闭' } : row)));
                            setNotice(`${item.id} 已关闭`);
                          }}
                        >
                          关闭帖子
                        </button>
                      )}
                      <button className="btn off" onClick={() => setNotice(`${item.id} 的管理回复入口已记录`)}>
                        管理回复
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination ariaLabel="求资源帖子管理分页" total={requestRows.length} />
            <p className="tip">违规帖子可关闭，申诉通过后支持恢复</p>
          </Panel>
        )}

        {activeTab === 'comment' && (
          <Panel>
            <table>
              <thead>
                <tr>
                  <th>评论ID</th>
                  <th>评论内容</th>
                  <th>归属资源</th>
                  <th>当前状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {commentRows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.content}</td>
                    <td>{item.target}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === '已删除' ? (
                        <button
                          className="btn restore"
                          onClick={() => {
                            setCommentRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '正常' } : row)));
                            setNotice(`${item.id} 已恢复`);
                          }}
                        >
                          恢复评论
                        </button>
                      ) : (
                        <button
                          className="btn del"
                          onClick={() => {
                            setCommentRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已删除' } : row)));
                            setNotice(`${item.id} 已删除`);
                          }}
                        >
                          删除评论
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination ariaLabel="评论内容管理分页" total={commentRows.length} />
            <p className="tip">删除评论前台隐藏，可按需恢复展示</p>
          </Panel>
        )}
      </section>
    </>
  );
}

function UsersPage({
  openModal,
  setNotice,
}: {
  openModal: (modal: ModalState) => void;
  setNotice: (message: string) => void;
}) {
  const [userRows, setUserRows] = useStoredState<AdminUser[]>('rsf_admin_user_rows', users);

  return (
    <Panel title="用户账号管理">
      <table>
        <thead>
          <tr>
            <th>用户ID</th>
            <th>用户昵称</th>
            <th>注册时间</th>
            <th>账号状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {userRows.map((item) => (
            <tr key={item.id}>
              <td>{item.id}</td>
              <td>{item.nickname}</td>
              <td>{item.registeredAt}</td>
              <td>{item.status}</td>
              <td>
                {item.status === '已禁用' ? (
                  <button
                    className="btn restore"
                    onClick={() => {
                      setUserRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '正常' } : row)));
                      setNotice(`${item.id} 已恢复账号`);
                    }}
                  >
                    恢复账号
                  </button>
                ) : (
                  <button
                    className="btn forbid"
                    onClick={() =>
                      openModal({
                        title: '填写禁用原因',
                        placeholder: '请输入账号禁用缘由',
                        confirmText: '确认禁用',
                        message: `${item.id} 已禁用，用户将无法登录`,
                        onConfirm: () =>
                          setUserRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已禁用' } : row))),
                      })
                    }
                  >
                    禁用账号
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination ariaLabel="用户账号管理分页" total={userRows.length} />
      <p className="tip">说明：禁用后用户无法登录，其已发布的资源前台不可见；恢复账号后，其资源需单独处理。</p>
    </Panel>
  );
}

function ReportsPage({
  openModal,
  setNotice,
}: {
  openModal: (modal: ModalState) => void;
  setNotice: (message: string) => void;
}) {
  const [activeTab, setActiveTab] = useState('report');
  const [reportRows, setReportRows] = useStoredState<AdminReport[]>('rsf_admin_report_rows', reports);
  const [complaintRows, setComplaintRows] = useStoredState<AdminComplaint[]>('rsf_admin_complaint_rows', complaints);
  const tabs = [
    { id: 'report', label: '举报处理' },
    { id: 'copyright', label: '版权投诉处理' },
  ];

  return (
    <>
      <TabHead tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />
      <section className="tab-content">
        {activeTab === 'report' && (
          <Panel>
            <table>
              <thead>
                <tr>
                  <th>举报ID</th>
                  <th>对象ID</th>
                  <th>举报对象</th>
                  <th>举报类型</th>
                  <th>当前状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {reportRows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.targetId}</td>
                    <td>{item.target}</td>
                    <td>{item.type}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === '待处理' ? (
                        <>
                          <button
                            className="btn reject"
                            onClick={() =>
                              openModal({
                                title: '填写处理结果',
                                placeholder: '请输入处理意见与结果',
                                confirmText: '提交',
                                message: `${item.id} 已驳回并记录处理结果`,
                                onConfirm: () =>
                                  setReportRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已驳回' } : row))),
                              })
                            }
                          >
                            驳回
                          </button>
                          <button
                            className={item.action === 'delete-comment' ? 'btn del' : 'btn off'}
                            onClick={() => {
                              setReportRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已处理' } : row)));
                              setNotice(`${item.id} 已处理`);
                            }}
                          >
                            {item.action === 'delete-comment' ? '删除评论' : item.action === 'offline-resource' ? '下架资源' : '下架帖子'}
                          </button>
                        </>
                      ) : (
                        <span className="muted">已处理</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination ariaLabel="举报处理分页" total={reportRows.length} />
            <p className="tip">驳回需填写原因；下架/删除操作会同步更新被举报对象状态，并记录处理结果。</p>
          </Panel>
        )}

        {activeTab === 'copyright' && (
          <Panel>
            <table>
              <thead>
                <tr>
                  <th>投诉ID</th>
                  <th>资源ID</th>
                  <th>资源名称</th>
                  <th>投诉方</th>
                  <th>当前状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {complaintRows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.resourceId}</td>
                    <td>{item.resourceName}</td>
                    <td>{item.complainant}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === '待审核' ? (
                        <>
                          <button
                            className="btn reject"
                            onClick={() =>
                              openModal({
                                title: '填写处理结果',
                                placeholder: '请输入处理意见与结果',
                                confirmText: '提交',
                                message: `${item.id} 已驳回投诉`,
                                onConfirm: () =>
                                  setComplaintRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已驳回' } : row))),
                              })
                            }
                          >
                            驳回投诉
                          </button>
                          <button
                            className="btn off"
                            onClick={() => {
                              setComplaintRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: '已处理' } : row)));
                              setNotice(`${item.id} 已通过并强制下架资源`);
                            }}
                          >
                            通过并强制下架
                          </button>
                        </>
                      ) : (
                        <span className="muted">已处理</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination ariaLabel="版权投诉处理分页" total={complaintRows.length} />
            <p className="tip">版权投诉通过后，系统强制下架资源并锁定，资源不可恢复。</p>
          </Panel>
        )}
      </section>
    </>
  );
}

function CategoriesPage({
  openModal,
  setNotice,
}: {
  openModal: (modal: ModalState) => void;
  setNotice: (message: string) => void;
}) {
  const [categoryRows, setCategoryRows] = useStoredState<AdminCategory[]>('rsf_admin_category_rows', categories);

  return (
    <Panel title="分类标签管理">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>类型</th>
            <th>所属一级分类</th>
            <th>关联资源数</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {categoryRows.map((item) => (
            <tr key={item.id}>
              <td>{item.id}</td>
              <td>{item.name}</td>
              <td>{item.type}</td>
              <td>{item.parent}</td>
              <td>{item.relationCount}</td>
              <td>{item.status}</td>
              <td>
                <button
                  className="btn edit"
                  onClick={() =>
                    openModal({
                      title: '分类/标签设置',
                      placeholder: '请输入名称',
                      confirmText: '提交',
                      message: `${item.id} 分类/标签设置已保存`,
                      onConfirm: (name) => {
                        const nextName = name.trim();
                        if (!nextName) return;
                        setCategoryRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, name: nextName } : row)));
                      },
                    })
                  }
                >
                  编辑
                </button>
                <button
                  className={item.status === '启用' ? 'btn disable' : 'btn enable'}
                  onClick={() => {
                    const nextStatus = item.status === '启用' ? '禁用' : '启用';
                    setCategoryRows((rows) => rows.map((row) => (row.id === item.id ? { ...row, status: nextStatus } : row)));
                    setNotice(`${item.id} 已${nextStatus}`);
                  }}
                >
                  {item.status === '启用' ? '禁用' : '启用'}
                </button>
                <button
                  className="btn del"
                  onClick={() => {
                    if (item.relationCount > 0) {
                      setNotice(`${item.id} 存在关联资源，暂不可删除`);
                      return;
                    }
                    setCategoryRows((rows) => rows.filter((row) => row.id !== item.id));
                    setNotice(`${item.id} 已删除`);
                  }}
                >
                  删除
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="toolbar">
        <button
          className="btn add"
          onClick={() =>
            openModal({
              title: '分类/标签设置',
              placeholder: '请输入名称',
              confirmText: '提交',
              message: '新增分类/标签已保存',
              onConfirm: (name) =>
                setCategoryRows((rows) => [
                  ...rows,
                  {
                    id: `T${String(rows.length + 1).padStart(3, '0')}`,
                    name: name.trim() || '新建标签',
                    type: '标签',
                    parent: '-',
                    relationCount: 0,
                    status: '启用',
                  },
                ]),
            })
          }
        >
          新增分类/标签
        </button>
      </div>
      <Pagination ariaLabel="分类标签管理分页" total={categoryRows.length} />
      <p className="tip">说明：禁用后不可用于新资源发布；删除前需校验关联资源数量，避免数据丢失。</p>
    </Panel>
  );
}

function ConfigPage({ setNotice }: { setNotice: (message: string) => void }) {
  const [levels, setLevels] = useStoredState<MemberLevel[]>('rsf_admin_member_levels', memberLevels);
  const [scoreRules, setScoreRules] = useStoredState<ConfigItem[]>('rsf_admin_score_rules', [
    { label: '资源审核通过 + 积分', value: '10' },
    { label: '资源被下载 + 积分/次', value: '2' },
    { label: '求资源回答被采纳 + 奖励积分', value: '5' },
    { label: '资源违规下架 - 扣积分', value: '20' },
    { label: '评论违规删除 - 扣积分', value: '5' },
  ]);
  const [systemParams, setSystemParams] = useStoredState<ConfigItem[]>('rsf_admin_system_params', [
    { label: '允许上传文件类型', value: 'pdf,doc,docx,ppt,pptx,xls,xlsx,zip,rar,7z,png,jpg,jpeg,txt,md' },
    { label: '用户每日最大发布资源数', value: '5' },
    { label: '邮箱验证码有效期（分钟）', value: '10' },
    { label: '连续登录失败锁定次数', value: '5' },
    { label: '登录失败锁定时长（分钟）', value: '10' },
  ]);

  return (
    <div className="config-page">
      <Panel title="会员等级配置">
        <table>
          <thead>
            <tr>
              <th>等级名称</th>
              <th>积分下限</th>
              <th>积分上限</th>
              <th>每日下载次数</th>
              <th>单资源最大附件数</th>
              <th>悬赏积分上限</th>
              <th>资源置顶资格</th>
            </tr>
          </thead>
          <tbody>
            {levels.map((level, index) => (
              <tr key={level.name}>
                <td>
                  <input className="edit-name" value={level.name} onChange={(event) => updateLevel(setLevels, index, 'name', event.target.value)} />
                </td>
                <td>
                  <input className="edit-input" value={level.min} onChange={(event) => updateLevel(setLevels, index, 'min', event.target.value)} />
                </td>
                <td>
                  <input className="edit-input" value={level.max} onChange={(event) => updateLevel(setLevels, index, 'max', event.target.value)} />
                </td>
                <td>
                  <input className="edit-input" value={level.downloads} onChange={(event) => updateLevel(setLevels, index, 'downloads', event.target.value)} />
                </td>
                <td>
                  <input className="edit-input" value={level.files} onChange={(event) => updateLevel(setLevels, index, 'files', event.target.value)} />
                </td>
                <td>
                  <input className="edit-input" value={level.rewardLimit} onChange={(event) => updateLevel(setLevels, index, 'rewardLimit', event.target.value)} />
                </td>
                <td>
                  <input className="edit-input" value={level.canTop} onChange={(event) => updateLevel(setLevels, index, 'canTop', event.target.value)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <button className="btn-save" onClick={() => setNotice('会员配置已保存')}>
          保存会员配置
        </button>
      </Panel>

      <Panel title="积分规则配置">
        <ConfigForm items={scoreRules} onChange={setScoreRules} buttonText="保存积分规则" onSave={() => setNotice('积分规则已保存')} />
      </Panel>

      <Panel title="全局系统参数">
        <ConfigForm items={systemParams} onChange={setSystemParams} buttonText="保存系统参数" onSave={() => setNotice('系统参数已保存')} />
      </Panel>
    </div>
  );
}

function updateLevel(
  setLevels: (updater: (levels: MemberLevel[]) => MemberLevel[]) => void,
  index: number,
  key: keyof MemberLevel,
  value: string,
) {
  setLevels((levels) => levels.map((level, levelIndex) => (levelIndex === index ? { ...level, [key]: value } : level)));
}

function LogsPage() {
  const [draftType, setDraftType] = useState('');
  const [appliedType, setAppliedType] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 6;

  const filteredLogs = useMemo(() => logs.filter((log) => matchesLogType(log, appliedType)), [appliedType]);
  const visibleLogs = filteredLogs.slice((page - 1) * pageSize, page * pageSize);

  return (
    <Panel title="操作审计日志">
      <div className="search-row">
        <label className="search-label" htmlFor="log-type">
          操作类型
        </label>
        <select id="log-type" className="search-select" value={draftType} onChange={(event) => setDraftType(event.target.value)}>
          <option value="">全部操作类型</option>
          <option>账号登录</option>
          <option>资源审核</option>
          <option>资源上下架</option>
          <option>用户状态变更</option>
          <option>评论管理</option>
          <option>举报投诉处理</option>
          <option>分类标签维护</option>
          <option>等级权益配置</option>
        </select>
        <button
          className="btn-search"
          onClick={() => {
            setAppliedType(draftType);
            setPage(1);
          }}
        >
          查询
        </button>
        <span className="result-count">共 {filteredLogs.length} 条记录</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>操作时间</th>
            <th>管理员ID</th>
            <th>IP地址</th>
            <th>操作类型</th>
            <th>操作对象</th>
            <th>对象ID</th>
            <th>变更前</th>
            <th>变更后</th>
            <th>操作结果</th>
          </tr>
        </thead>
        <tbody>
          {visibleLogs.map((log) => (
            <tr key={`${log.time}-${log.type}-${log.targetId}`}>
              <td>{log.time}</td>
              <td>{log.adminId}</td>
              <td>{log.ip}</td>
              <td>{log.type}</td>
              <td>{log.target}</td>
              <td>{log.targetId}</td>
              <td>{log.before}</td>
              <td>{log.after}</td>
              <td>{log.result}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination ariaLabel="操作审计日志分页" total={filteredLogs.length} page={page} pageSize={pageSize} onPageChange={setPage} />
    </Panel>
  );
}

function matchesLogType(log: AdminLog, type: string) {
  if (!type) return true;
  if (type === '资源上下架') return log.type === '资源下架' || log.type === '资源恢复';
  if (type === '用户状态变更') return log.type === '用户禁用' || log.type === '用户恢复';
  if (type === '评论管理') return log.type === '评论删除' || log.type === '评论恢复';
  if (type === '举报投诉处理') return log.type === '举报处理' || log.type === '版权投诉处理';
  if (type === '分类标签维护') return log.type.startsWith('分类') || log.type.startsWith('标签');
  return log.type === type;
}

function ConfigForm({
  items,
  onChange,
  buttonText,
  onSave,
}: {
  items: ConfigItem[];
  onChange: (updater: (items: ConfigItem[]) => ConfigItem[]) => void;
  buttonText: string;
  onSave: () => void;
}) {
  return (
    <>
      <div className="config-form">
        {items.map((item, index) => (
          <label className="form-item" key={item.label}>
            <span className="form-label">{item.label}</span>
            <input
              className="form-input"
              value={item.value}
              onChange={(event) =>
                onChange((currentItems) => currentItems.map((currentItem, itemIndex) => (itemIndex === index ? { ...currentItem, value: event.target.value } : currentItem)))
              }
            />
          </label>
        ))}
      </div>
      <button className="btn-save" onClick={onSave}>
        {buttonText}
      </button>
    </>
  );
}

function TabHead({
  tabs,
  activeTab,
  onChange,
}: {
  tabs: Array<{ id: string; label: string }>;
  activeTab: string;
  onChange: (id: string) => void;
}) {
  return (
    <div className="tab-head">
      {tabs.map((tab) => (
        <button key={tab.id} className={tab.id === activeTab ? 'tab-item active' : 'tab-item'} onClick={() => onChange(tab.id)}>
          {tab.label}
        </button>
      ))}
    </div>
  );
}

function Panel({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="panel">
      {title ? <div className="card-title">{title}</div> : null}
      {children}
    </section>
  );
}

function Pagination({
  ariaLabel,
  total,
  page = 1,
  pageSize = 10,
  onPageChange,
}: {
  ariaLabel: string;
  total: number;
  page?: number;
  pageSize?: number;
  onPageChange?: (page: number) => void;
}) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const pages = Array.from({ length: pageCount }, (_, index) => index + 1);
  const canPrev = page > 1;
  const canNext = page < pageCount;

  return (
    <div className="page-box" aria-label={ariaLabel}>
      <button className="page-btn" disabled={!canPrev} onClick={() => canPrev && onPageChange?.(page - 1)}>
        上一页
      </button>
      {pages.map((pageNumber) => (
        <button key={pageNumber} className={pageNumber === page ? 'page-btn active' : 'page-btn'} onClick={() => onPageChange?.(pageNumber)}>
          {pageNumber}
        </button>
      ))}
      <button className="page-btn" disabled={!canNext} onClick={() => canNext && onPageChange?.(page + 1)}>
        下一页
      </button>
    </div>
  );
}

function ReasonModal({
  modal,
  onClose,
  onConfirm,
}: {
  modal: ModalState;
  onClose: () => void;
  onConfirm: (message: string) => void;
}) {
  const [text, setText] = useState('');
  const location = useLocation();

  useEffect(() => {
    setText('');
  }, [location.pathname, modal?.title]);

  if (!modal) return null;

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label={modal.title}>
      <div className="modal-box">
        <div className="modal-title">{modal.title}</div>
        <textarea className="modal-textarea" placeholder={modal.placeholder} value={text} onChange={(event) => setText(event.target.value)} />
        <div className="modal-actions">
          <button className="modal-btn cancel" onClick={onClose}>
            取消
          </button>
          <button
            className="modal-btn confirm"
            onClick={() => {
              modal.onConfirm?.(text);
              onConfirm(modal.message);
            }}
          >
            {modal.confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
