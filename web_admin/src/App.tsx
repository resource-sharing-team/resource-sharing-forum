import { type ReactNode, useCallback, useEffect, useState } from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import {
  approveResource,
  ADMIN_AUTH_EXPIRED_EVENT,
  backfillNormativeTags,
  clearAdminSession,
  closeRequest,
  copyrightDownResource,
  createCategory,
  createTag,
  deleteComment,
  deleteResource,
  disableCategory,
  disableTag,
  disableUser,
  enableUser,
  getAdminCatalogPage,
  getAdminCatalogOptions,
  getAdminConfigFull,
  getAdminCompliancePage,
  getAdminContentPage,
  getAdminLogs,
  getAdminUsers,
  handleAppeal,
  handleReport,
  hideComment,
  LEGACY_ADMIN_SESSION_KEY,
  loginAdmin,
  mergeTags,
  offlineResource,
  readAdminSession,
  refreshConfigCache,
  rejectResource,
  restoreComment,
  restoreResource,
  storeAdminSession,
  updateCategory,
  updateConfigItem,
  updateMemberLevel,
  updateTag,
} from './api/admin';
import type {
  AdminAppeal,
  AdminCategory,
  AdminCatalogOptions,
  AdminCatalogTagCandidate,
  AdminCatalogSection,
  AdminComment,
  AdminComplaint,
  AdminComplianceSection,
  AdminConfigData,
  AdminContentSection,
  AdminLog,
  AdminReport,
  AdminReportAction,
  AdminRequestPost,
  AdminResource,
  AdminSession,
  AdminUser,
  ConfigItem,
  MemberLevel,
  PageResult,
  RawId,
} from './types';

const navItems = [
  { to: '/', label: '内容综合管理' },
  { to: '/users', label: '用户账号管理' },
  { to: '/reports', label: '举报版权投诉' },
  { to: '/categories', label: '分类标签管理' },
  { to: '/config', label: '系统参数配置' },
  { to: '/logs', label: '操作审计日志' },
];

const ADMIN_PAGE_SIZE = 10;
const ADMIN_LOG_PAGE_SIZE = 8;

const emptyConfig: AdminConfigData = {
  memberLevels: [],
  scoreRules: [],
  systemParams: [],
};

type ModalState = null | {
  title: string;
  placeholder: string;
  confirmText: string;
  message?: string;
  onConfirm?: (reason: string) => Promise<string | void> | string | void;
};

type AdminContentRow = AdminResource | AdminRequestPost | AdminComment;
type AdminComplianceRow = AdminReport | AdminComplaint | AdminAppeal;
type CatalogFilter = 'all' | 'level1' | 'level2' | 'tag';
type CatalogModalState = null | 'first-category' | 'second-category' | 'tag';

export default function App() {
  const [session, setSession] = useState<AdminSession | null>(() => {
    localStorage.removeItem(LEGACY_ADMIN_SESSION_KEY);
    const stored = readAdminSession();
    return stored?.role === 'ADMIN' ? stored : null;
  });

  const handleLogin = (nextSession: AdminSession) => {
    storeAdminSession(nextSession);
    setSession(nextSession);
  };

  const handleLogout = () => {
    clearAdminSession();
    setSession(null);
  };

  useEffect(() => {
    const handleExpired = () => setSession(null);
    window.addEventListener(ADMIN_AUTH_EXPIRED_EVENT, handleExpired);
    return () => window.removeEventListener(ADMIN_AUTH_EXPIRED_EVENT, handleExpired);
  }, []);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
        <Route path="/*" element={session ? <AdminShell session={session} onLogout={handleLogout} /> : <Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function LoginPage({ onLogin }: { onLogin: (session: AdminSession) => void }) {
  const navigate = useNavigate();
  const [account, setAccount] = useState('admin');
  const [password, setPassword] = useState('password');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

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
          onSubmit={async (event) => {
            event.preventDefault();
            setSubmitting(true);
            setError('');
            try {
              const nextSession = await loginAdmin(account, password);
              onLogin(nextSession);
              navigate('/', { replace: true });
            } catch (loginError) {
              setError(errorMessage(loginError));
            } finally {
              setSubmitting(false);
            }
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
          <button type="submit" className="btn-login" disabled={submitting}>
            {submitting ? '登录中...' : '登录后台'}
          </button>
        </form>
        <p className="login-footer">Resource Sharing Forum · 管理入口</p>
      </section>
    </main>
  );
}

function AdminShell({ session, onLogout }: { session: AdminSession; onLogout: () => void }) {
  const navigate = useNavigate();
  const [modal, setModal] = useState<ModalState>(null);
  const [notice, setNotice] = useState('');
  const adminName = session.user.nickname || session.user.username || '管理员账号';

  const openModal = (nextModal: ModalState) => {
    setNotice('');
    setModal(nextModal);
  };

  return (
    <div className="admin-page">
      <header className="top">
        <h3>管理员管理系统</h3>
        <div className="top-account">
          <span>{adminName}</span>
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
          if (message) setNotice(message);
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
  const [activeTab, setActiveTab] = useState<AdminContentSection>('audit');
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<PageResult<AdminContentRow>>(() => emptyPage<AdminContentRow>());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await getAdminContentPage(activeTab, { page: String(page), size: String(ADMIN_PAGE_SIZE) }));
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [activeTab, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const reloadAfterAction = async () => {
    if (page !== 1) {
      setPage(1);
      return;
    }
    await load();
  };

  const runAction = async (operation: Promise<unknown>, message: string) => {
    try {
      await operation;
      await reloadAfterAction();
      setNotice(message);
    } catch (actionError) {
      setNotice(errorMessage(actionError));
    }
  };

  const tabs: Array<{ id: AdminContentSection; label: string }> = [
    { id: 'audit', label: '资源审核列表' },
    { id: 'resource', label: '资源上下架管理' },
    { id: 'request', label: '求资源帖子管理' },
    { id: 'comment', label: '评论内容管理' },
  ];
  const resourceRows = rowsOf(result) as AdminResource[];
  const requestRows = rowsOf(result) as AdminRequestPost[];
  const commentRows = rowsOf(result) as AdminComment[];

  if (loading) return <Panel><LoadingState /></Panel>;
  if (error) return <Panel><ErrorState message={error} onRetry={load} /></Panel>;

  return (
    <>
      <TabHead
        tabs={tabs}
        activeTab={activeTab}
        onChange={(id) => {
          setActiveTab(id as AdminContentSection);
          setPage(1);
        }}
      />
      <section className="tab-content">
        {activeTab === 'audit' && (
          <Panel>
            <TableWrap>
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
                  {resourceRows.map((item) => (
                    <tr key={item.id}>
                      <td>{item.id}</td>
                      <td>{item.title}</td>
                      <td>{item.user || '-'}</td>
                      <td><StatusBadge status={item.status} /></td>
                      <td>
                        {statusIn(item, 'PENDING_REVIEW', '待审核') ? (
                          <>
                            <button className="btn pass" onClick={() => void runAction(approveResource(entityId(item)), `${item.id} 已通过审核`)}>
                              通过审核
                            </button>
                            <button
                              className="btn reject"
                              onClick={() =>
                                openModal({
                                  title: '填写驳回原因',
                                  placeholder: '请输入驳回缘由',
                                  confirmText: '确认驳回',
                                  onConfirm: async (reason) => {
                                    await rejectResource(entityId(item), requiredReason(reason));
                                    await reloadAfterAction();
                                    return `${item.id} 已驳回，原因已记录`;
                                  },
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
                  <EmptyRow colSpan={5} show={resourceRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="资源审核列表分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
            <p className="tip">驳回操作需填写原因，审核通过后前台正常展示</p>
          </Panel>
        )}

        {activeTab === 'resource' && (
          <Panel>
            <TableWrap>
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
                  {resourceRows.map((item) => (
                    <tr key={item.id}>
                      <td>{item.id}</td>
                      <td>{item.title}</td>
                      <td>{item.user || '-'}</td>
                      <td><StatusBadge status={item.status} /></td>
                      <td>
                        {statusIn(item, 'PUBLISHED', '已发布') && (
                          <>
                            <button
                              className="btn off"
                              onClick={() =>
                                openModal({
                                  title: '填写下架原因',
                                  placeholder: '请输入资源下架原因',
                                  confirmText: '确认下架',
                                  onConfirm: async (reason) => {
                                    await offlineResource(entityId(item), requiredReason(reason));
                                    await reloadAfterAction();
                                    return `${item.id} 已下架`;
                                  },
                                })
                              }
                            >
                              下架
                            </button>
                            <button
                              className="btn reject"
                              onClick={() =>
                                openModal({
                                  title: '填写版权处理结果',
                                  placeholder: '请输入版权下架原因',
                                  confirmText: '版权下架',
                                  onConfirm: async (reason) => {
                                    await copyrightDownResource(entityId(item), requiredReason(reason));
                                    await reloadAfterAction();
                                    return `${item.id} 已版权下架`;
                                  },
                                })
                              }
                            >
                              版权下架
                            </button>
                          </>
                        )}
                        {statusIn(item, 'OFFLINE', 'RISK_REVIEW', 'REVIEWING_RISK', '已下架', '风险复核') && (
                          <button className="btn restore" onClick={() => void runAction(restoreResource(entityId(item)), `${item.id} 已恢复上架`)}>
                            恢复上架
                          </button>
                        )}
                        {!statusIn(item, 'DELETED', '已删除') && (
                          <button
                            className="btn del"
                            onClick={() =>
                              openModal({
                                title: '填写删除原因',
                                placeholder: '请输入管理员删除原因',
                                confirmText: '确认删除',
                                onConfirm: async (reason) => {
                                  await deleteResource(entityId(item), requiredReason(reason));
                                  await reloadAfterAction();
                                  return `${item.id} 已删除`;
                                },
                              })
                            }
                          >
                            删除
                          </button>
                        )}
                        {statusIn(item, 'COPYRIGHT_DOWN', '版权下架') && <span className="muted">版权锁定</span>}
                      </td>
                    </tr>
                  ))}
                  <EmptyRow colSpan={5} show={resourceRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="资源上下架管理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
            <p className="tip">下架资源前台隐藏，版权下架资源保持处理原因和后台日志</p>
          </Panel>
        )}

        {activeTab === 'request' && (
          <Panel>
            <TableWrap>
              <table>
                <thead>
                  <tr>
                    <th>帖子ID</th>
                    <th>求助标题</th>
                    <th>发布用户/发帖人</th>
                    <th>当前状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {requestRows.map((item) => (
                    <tr key={item.id}>
                      <td>{item.id}</td>
                      <td>{item.title}</td>
                      <td>{item.user || '-'}</td>
                      <td><StatusBadge status={item.status} /></td>
                      <td>
                        {statusIn(item, 'CLOSED', '已关闭') ? (
                          <span className="muted">已关闭</span>
                        ) : (
                          <button
                            className="btn del"
                            onClick={() =>
                              openModal({
                                title: '填写关闭原因',
                                placeholder: '请输入关闭求资源帖原因',
                                confirmText: '确认关闭',
                                onConfirm: async (reason) => {
                                  await closeRequest(entityId(item), requiredReason(reason));
                                  await reloadAfterAction();
                                  return `${item.id} 已关闭`;
                                },
                              })
                            }
                          >
                            关闭帖子
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  <EmptyRow colSpan={5} show={requestRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="求资源帖子管理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
            <p className="tip">违规帖子可关闭；回复删除由举报处理联动执行并写入后台日志</p>
          </Panel>
        )}

        {activeTab === 'comment' && (
          <Panel>
            <TableWrap>
              <table>
                <thead>
                  <tr>
                    <th>评论ID</th>
                    <th>评论内容</th>
                    <th>归属对象</th>
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
                      <td><StatusBadge status={item.status} /></td>
                      <td>
                        {statusIn(item, 'ACTIVE', '正常') ? (
                          <>
                            <button className="btn off" onClick={() => void runAction(hideComment(entityId(item)), `${item.id} 已隐藏`)}>
                              隐藏
                            </button>
                            <button
                              className="btn del"
                              onClick={() =>
                                openModal({
                                  title: '填写删除原因',
                                  placeholder: '请输入评论删除原因',
                                  confirmText: '确认删除',
                                  onConfirm: async () => {
                                    await deleteComment(entityId(item));
                                    await reloadAfterAction();
                                    return `${item.id} 已删除`;
                                  },
                                })
                              }
                            >
                              删除评论
                            </button>
                          </>
                        ) : (
                          <button className="btn restore" onClick={() => void runAction(restoreComment(entityId(item)), `${item.id} 已恢复`)}>
                            恢复评论
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  <EmptyRow colSpan={5} show={commentRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="评论内容管理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
            <p className="tip">删除评论前台隐藏，必要时可恢复展示</p>
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
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<PageResult<AdminUser>>(() => emptyPage<AdminUser>());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await getAdminUsers({ keyword, status, page: String(page), size: String(ADMIN_PAGE_SIZE) }));
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [keyword, page, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = rowsOf(result);
  const reloadAfterAction = async () => {
    if (page !== 1) {
      setPage(1);
      return;
    }
    await load();
  };

  return (
    <Panel title="用户账号管理">
      <div className="search-row">
        <label className="search-label" htmlFor="user-keyword">关键字</label>
        <input
          id="user-keyword"
          className="search-input"
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setPage(1);
          }}
          placeholder="昵称 / 账号 / 邮箱"
        />
        <label className="search-label" htmlFor="user-status">状态</label>
        <select
          id="user-status"
          className="search-select"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(1);
          }}
        >
          <option value="">全部状态</option>
          <option value="正常">正常</option>
          <option value="已禁用">已禁用</option>
          <option value="已锁定">已锁定</option>
        </select>
        <button className="btn-search" onClick={() => setPage(1)}>查询</button>
        <span className="result-count">共 {result.total} 条记录</span>
      </div>
      {loading ? <LoadingState /> : error ? <ErrorState message={error} onRetry={load} /> : (
        <>
          <TableWrap>
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
                {rows.map((item) => (
                  <tr key={item.id}>
                    <td>{item.id}</td>
                    <td>{item.nickname}</td>
                    <td>{item.registeredAt}</td>
                    <td><StatusBadge status={item.status} /></td>
                    <td>
                      {statusIn(item, 'DISABLED', '已禁用') ? (
                        <button
                          className="btn restore"
                          onClick={async () => {
                            try {
                              await enableUser(entityId(item));
                              await reloadAfterAction();
                              setNotice(`${item.id} 已恢复账号`);
                            } catch (actionError) {
                              setNotice(errorMessage(actionError));
                            }
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
                              onConfirm: async (reason) => {
                                await disableUser(entityId(item), requiredReason(reason));
                                await reloadAfterAction();
                                return `${item.id} 已禁用，用户将无法登录`;
                              },
                            })
                          }
                        >
                          禁用账号
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                <EmptyRow colSpan={5} show={rows.length === 0} />
              </tbody>
            </table>
          </TableWrap>
          <Pagination ariaLabel="用户账号管理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
        </>
      )}
      <p className="tip">说明：禁用后用户无法登录；恢复账号后，资源状态仍按资源管理页单独处理。</p>
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
  const [activeTab, setActiveTab] = useState<AdminComplianceSection>('report');
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<PageResult<AdminComplianceRow>>(() => emptyPage<AdminComplianceRow>());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await getAdminCompliancePage(activeTab, { page: String(page), size: String(ADMIN_PAGE_SIZE) }));
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [activeTab, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const reloadAfterAction = async () => {
    if (page !== 1) {
      setPage(1);
      return;
    }
    await load();
  };

  const tabs: Array<{ id: AdminComplianceSection; label: string }> = [
    { id: 'report', label: '举报处理' },
    { id: 'copyright', label: '版权投诉处理' },
    { id: 'appeal', label: '申诉处理' },
  ];
  const reportRows = rowsOf(result) as AdminReport[];
  const complaintRows = rowsOf(result) as AdminComplaint[];
  const appealRows = rowsOf(result) as AdminAppeal[];

  if (loading) return <Panel><LoadingState /></Panel>;
  if (error) return <Panel><ErrorState message={error} onRetry={load} /></Panel>;

  return (
    <>
      <TabHead
        tabs={tabs}
        activeTab={activeTab}
        onChange={(id) => {
          setActiveTab(id as AdminComplianceSection);
          setPage(1);
        }}
      />
      <section className="tab-content">
        {activeTab === 'report' && (
          <Panel>
            <TableWrap>
              <table>
                <thead>
                  <tr>
                    <th>举报ID</th>
                    <th>举报对象</th>
                    <th>举报类型</th>
                    <th>处理动作</th>
                    <th>当前状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {reportRows.map((item) => (
                    <tr key={item.id}>
                      <td>{item.id}</td>
                      <td>{item.target}（{item.targetId}）</td>
                      <td>{item.type}</td>
                      <td>{reportActionLabel(item.action)}</td>
                      <td><StatusBadge status={item.status} /></td>
                      <td>{renderReportActions(item, openModal, reloadAfterAction, setNotice)}</td>
                    </tr>
                  ))}
                  <EmptyRow colSpan={6} show={reportRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="举报处理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
            <p className="tip">举报处理可联动删除评论、下架资源、关闭帖子、删除回复或禁用用户。</p>
          </Panel>
        )}

        {activeTab === 'copyright' && (
          <Panel>
            <TableWrap>
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
                      <td><StatusBadge status={item.status} /></td>
                      <td>{renderComplaintActions(item, openModal, reloadAfterAction)}</td>
                    </tr>
                  ))}
                  <EmptyRow colSpan={6} show={complaintRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="版权投诉处理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
            <p className="tip">版权投诉通过后，系统强制版权下架资源并记录处理结果。</p>
          </Panel>
        )}

        {activeTab === 'appeal' && (
          <Panel>
            <TableWrap>
              <table>
                <thead>
                  <tr>
                    <th>申诉ID</th>
                    <th>申诉对象</th>
                    <th>申诉人</th>
                    <th>申诉说明</th>
                    <th>当前状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {appealRows.map((item) => (
                    <tr key={item.id}>
                      <td>{item.id}</td>
                      <td>{item.targetType}:{item.targetId}</td>
                      <td>{item.appellant || '-'}</td>
                      <td>{item.reason || '-'}</td>
                      <td><StatusBadge status={item.status} /></td>
                      <td>{renderAppealActions(item, openModal, reloadAfterAction)}</td>
                    </tr>
                  ))}
                  <EmptyRow colSpan={6} show={appealRows.length === 0} />
                </tbody>
              </table>
            </TableWrap>
            <Pagination ariaLabel="申诉处理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
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
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState<CatalogFilter>('all');
  const [result, setResult] = useState<PageResult<AdminCategory>>(() => emptyPage<AdminCategory>());
  const [options, setOptions] = useState<AdminCatalogOptions>(() => emptyCatalogOptions());
  const [catalogModal, setCatalogModal] = useState<CatalogModalState>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const query = catalogQuery(filter);
      const [pageResult, nextOptions] = await Promise.all([
        getAdminCatalogPage(query.section, { ...query.params, page: String(page), size: String(ADMIN_PAGE_SIZE) }),
        getAdminCatalogOptions(),
      ]);
      setResult(pageResult);
      setOptions(nextOptions);
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [filter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const rows = rowsOf(result);

  const reloadAfterAction = async () => {
    if (page !== 1) {
      setPage(1);
      return;
    }
    await load();
  };

  const changeFilter = (nextFilter: CatalogFilter) => {
    setFilter(nextFilter);
    setPage(1);
  };

  const runCatalogAction = async (operation: Promise<unknown>, message: string) => {
    try {
      await operation;
      await reloadAfterAction();
      setNotice(message);
    } catch (actionError) {
      setNotice(errorMessage(actionError));
    }
  };

  const runBackfill = async () => {
    try {
      const result = await backfillNormativeTags();
      await reloadAfterAction();
      setNotice(`已补齐 ${result.createdCount || 0} 个规范标签`);
    } catch (actionError) {
      setNotice(errorMessage(actionError));
    }
  };

  return (
    <Panel title="分类标签管理">
      {loading ? <LoadingState /> : error ? <ErrorState message={error} onRetry={load} /> : (
        <>
          <div className="catalog-tabs" role="tablist" aria-label="分类标签筛选">
            {catalogFilterItems.map((item) => (
              <button
                key={item.key}
                className={filter === item.key ? 'catalog-tab active' : 'catalog-tab'}
                onClick={() => changeFilter(item.key)}
              >
                {item.label}
              </button>
            ))}
          </div>
          <TableWrap>
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
                {rows.map((item) => (
                  <tr key={`${item.kind || item.type}-${item.id}`}>
                    <td>{item.id}</td>
                    <td>{item.name}</td>
                    <td>{item.type}</td>
                    <td>{item.parent}</td>
                    <td>{item.relationCount}</td>
                    <td><StatusBadge status={item.status} /></td>
                    <td>
                      {!isTag(item) && (
                        <button
                          className="btn edit"
                          onClick={() =>
                            openModal({
                              title: '分类设置',
                              placeholder: '请输入新名称',
                              confirmText: '提交',
                              onConfirm: async (name) => {
                                const nextName = name.trim();
                                if (!nextName) return '名称未修改';
                                await updateCategory(entityId(item), { name: nextName });
                                await reloadAfterAction();
                                return `${item.id} 分类设置已保存`;
                              },
                            })
                          }
                        >
                          编辑
                        </button>
                      )}
                      {statusIn(item, 'ENABLED', '启用') ? (
                        <button
                          className="btn disable"
                          onClick={() => void runCatalogAction(isTag(item) ? disableTag(entityId(item)) : disableCategory(entityId(item)), `${item.id} 已禁用`)}
                        >
                          禁用
                        </button>
                      ) : (
                        <button
                          className="btn enable"
                          onClick={() =>
                            void runCatalogAction(
                              isTag(item) ? updateTag(entityId(item), { status: 'ENABLED' }) : updateCategory(entityId(item), { status: 'ENABLED' }),
                              `${item.id} 已启用`,
                            )
                          }
                        >
                          启用
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                <EmptyRow colSpan={7} show={rows.length === 0} />
              </tbody>
            </table>
          </TableWrap>
          <div className="toolbar catalog-toolbar">
            <button className="btn add" onClick={() => setCatalogModal('first-category')}>新增一级分类</button>
            <button className="btn add" onClick={() => setCatalogModal('second-category')}>新增二级分类</button>
            <button className="btn add" onClick={() => setCatalogModal('tag')}>新增标签</button>
            <button className="btn edit" onClick={() => void runBackfill()}>一键补齐规范标签</button>
            <button
              className="btn edit"
              onClick={() =>
                openModal({
                  title: '合并标签',
                  placeholder: '请输入源标签ID,目标标签ID',
                  confirmText: '确认合并',
                  onConfirm: async (text) => {
                    const [source, target] = text.split(/[,\s，]+/).map((item) => item.trim()).filter(Boolean);
                    if (!source || !target) return '请输入源标签ID和目标标签ID';
                    await mergeTags(source, target);
                    await reloadAfterAction();
                    return `${source} 已合并到 ${target}`;
                  },
                })
              }
            >
              合并标签
            </button>
          </div>
          <Pagination ariaLabel="分类标签管理分页" total={result.total} page={page} pageSize={ADMIN_PAGE_SIZE} onPageChange={setPage} />
          <CatalogEntityModal
            mode={catalogModal}
            options={options}
            onClose={() => setCatalogModal(null)}
            onSubmit={async (payload) => {
              if (payload.mode === 'tag') {
                await createTag(payload.name);
                setCatalogModal(null);
                await reloadAfterAction();
                setNotice(`${payload.name} 标签已保存`);
                return;
              }
              await createCategory({
                name: payload.name,
                level: payload.mode === 'first-category' ? 1 : 2,
                parentId: payload.parentId,
                sortOrder: payload.sortOrder,
              });
              setCatalogModal(null);
              await reloadAfterAction();
              setNotice(payload.mode === 'first-category' ? '新增一级分类已保存' : '新增二级分类已保存');
            }}
          />
        </>
      )}
      <p className="tip">说明：禁用后不可用于新资源发布；标签合并会迁移资源和求资源关联。</p>
    </Panel>
  );
}

function ConfigPage({ setNotice }: { setNotice: (message: string) => void }) {
  const [config, setConfig] = useState<AdminConfigData>(emptyConfig);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setConfig(await getAdminConfigFull());
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const saveLevels = async () => {
    try {
      await Promise.all(config.memberLevels.map(updateMemberLevel));
      await load();
      setNotice('会员配置已保存');
    } catch (actionError) {
      setNotice(errorMessage(actionError));
    }
  };

  const saveConfigItems = async (items: ConfigItem[], message: string) => {
    try {
      await Promise.all(items.map(updateConfigItem));
      await refreshConfigCache();
      await load();
      setNotice(message);
    } catch (actionError) {
      setNotice(errorMessage(actionError));
    }
  };

  if (loading) return <Panel><LoadingState /></Panel>;
  if (error) return <Panel><ErrorState message={error} onRetry={load} /></Panel>;

  return (
    <div className="config-page">
      <Panel title="会员等级配置">
        <TableWrap>
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
              {config.memberLevels.map((level, index) => (
                <tr key={level.rawId || level.id || level.name}>
                  <td><input className="edit-name" value={level.name} onChange={(event) => updateLevel(setConfig, index, 'name', event.target.value)} /></td>
                  <td><input className="edit-input" value={level.min} onChange={(event) => updateLevel(setConfig, index, 'min', event.target.value)} /></td>
                  <td><input className="edit-input" value={level.max} onChange={(event) => updateLevel(setConfig, index, 'max', event.target.value)} /></td>
                  <td><input className="edit-input" value={level.downloads} onChange={(event) => updateLevel(setConfig, index, 'downloads', event.target.value)} /></td>
                  <td><input className="edit-input" value={level.files} onChange={(event) => updateLevel(setConfig, index, 'files', event.target.value)} /></td>
                  <td><input className="edit-input" value={level.rewardLimit} onChange={(event) => updateLevel(setConfig, index, 'rewardLimit', event.target.value)} /></td>
                  <td><input className="edit-input" value={level.canTop} onChange={(event) => updateLevel(setConfig, index, 'canTop', event.target.value)} /></td>
                </tr>
              ))}
              <EmptyRow colSpan={7} show={config.memberLevels.length === 0} />
            </tbody>
          </table>
        </TableWrap>
        <button className="btn-save" onClick={saveLevels}>保存会员配置</button>
      </Panel>

      <Panel title="积分规则配置">
        <ConfigForm
          items={config.scoreRules}
          onChange={(updater) => setConfig((current) => ({ ...current, scoreRules: updater(current.scoreRules) }))}
          buttonText="保存积分规则"
          onSave={() => void saveConfigItems(config.scoreRules, '积分规则已保存')}
        />
      </Panel>

      <Panel title="全局系统参数">
        <ConfigForm
          items={config.systemParams}
          onChange={(updater) => setConfig((current) => ({ ...current, systemParams: updater(current.systemParams) }))}
          buttonText="保存系统参数"
          onSave={() => void saveConfigItems(config.systemParams, '系统参数已保存')}
        />
      </Panel>
    </div>
  );
}

function LogsPage() {
  const [draftType, setDraftType] = useState('');
  const [appliedType, setAppliedType] = useState('');
  const [page, setPage] = useState(1);
  const [result, setResult] = useState<PageResult<AdminLog>>(() => emptyPage<AdminLog>(ADMIN_LOG_PAGE_SIZE));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const pageSize = ADMIN_LOG_PAGE_SIZE;

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setResult(await getAdminLogs({ type: appliedType, operationType: appliedType, page: String(page), size: String(pageSize) }));
    } catch (loadError) {
      setError(errorMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [appliedType, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleLogs = rowsOf(result);

  return (
    <Panel title="操作审计日志">
      <div className="search-row">
        <label className="search-label" htmlFor="log-type">操作类型</label>
        <select id="log-type" className="search-select" value={draftType} onChange={(event) => setDraftType(event.target.value)}>
          <option value="">全部操作类型</option>
          <option value="ACCOUNT_LOGIN">账号登录</option>
          <option value="RESOURCE_APPROVE">资源审核</option>
          <option value="RESOURCE_OFFLINE">资源下架</option>
          <option value="RESOURCE_RESTORE">资源恢复</option>
          <option value="MEMBER_DISABLED">用户禁用</option>
          <option value="MEMBER_NORMAL">用户恢复</option>
          <option value="COMMENT_DELETED">评论删除</option>
          <option value="REPORT_HANDLE">举报处理</option>
          <option value="APPEAL_HANDLE">申诉处理</option>
          <option value="CATEGORY_CREATE">分类新增</option>
          <option value="CATEGORY_ENABLE">分类启用</option>
          <option value="TAG_UPDATE">标签修改</option>
          <option value="TAG_BACKFILL">标签补齐</option>
          <option value="MEMBER_LEVEL_UPDATE">等级配置</option>
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
        <span className="result-count">共 {result.total} 条记录</span>
      </div>
      {loading ? <LoadingState /> : error ? <ErrorState message={error} onRetry={load} /> : (
        <>
          <TableWrap>
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
                    <td>{operationLabel(log.type)}</td>
                    <td>{targetLabel(log.target)}</td>
                    <td>{String(log.targetId ?? '-')}</td>
                    <td>{snapshotText(log.before)}</td>
                    <td>{snapshotText(log.after)}</td>
                    <td>{log.result}</td>
                  </tr>
                ))}
                <EmptyRow colSpan={9} show={visibleLogs.length === 0} />
              </tbody>
            </table>
          </TableWrap>
          <Pagination ariaLabel="操作审计日志分页" total={result.total} page={page} pageSize={pageSize} onPageChange={setPage} />
        </>
      )}
    </Panel>
  );
}

function renderReportActions(
  item: AdminReport,
  openModal: (modal: ModalState) => void,
  load: () => Promise<void>,
  setNotice: (message: string) => void,
) {
  if (!statusIn(item, 'PENDING', '待处理')) {
    return <span className="muted">已处理</span>;
  }
  return (
    <>
      <button
        className="btn reject"
        onClick={() =>
          openModal({
            title: '填写驳回原因',
            placeholder: '请输入举报驳回原因',
            confirmText: '确认驳回',
            onConfirm: async (reason) => {
              await handleReport(entityId(item), 'REJECTED', requiredReason(reason));
              await load();
              return `${item.id} 已驳回`;
            },
          })
        }
      >
        驳回
      </button>
      <button
        className={item.action === 'delete-comment' || item.action === 'delete-reply' || item.action === 'disable-user' ? 'btn del' : 'btn off'}
        onClick={() =>
          openModal({
            title: '填写处理结果',
            placeholder: '请输入处理意见与原因',
            confirmText: reportActionLabel(item.action),
            onConfirm: async (reason) => {
              await handleReport(entityId(item), 'RESOLVED', requiredReason(reason), item.action);
              await load();
              setNotice(`${item.id} 已处理`);
            },
          })
        }
      >
        {reportActionLabel(item.action)}
      </button>
    </>
  );
}

function renderComplaintActions(item: AdminComplaint, openModal: (modal: ModalState) => void, load: () => Promise<void>) {
  if (!statusIn(item, 'PENDING', '待审核')) {
    return <span className="muted">已处理</span>;
  }
  return (
    <>
      <button
        className="btn reject"
        onClick={() =>
          openModal({
            title: '填写处理结果',
            placeholder: '请输入驳回投诉原因',
            confirmText: '驳回投诉',
            onConfirm: async (reason) => {
              await handleReport(entityId(item), 'REJECTED', requiredReason(reason));
              await load();
              return `${item.id} 已驳回投诉`;
            },
          })
        }
      >
        驳回投诉
      </button>
      <button
        className="btn off"
        onClick={() =>
          openModal({
            title: '填写版权处理结果',
            placeholder: '请输入版权投诉通过原因',
            confirmText: '通过并下架',
            onConfirm: async (reason) => {
              await handleReport(entityId(item), 'RESOLVED', requiredReason(reason), 'copyright-down-resource');
              await load();
              return `${item.id} 已通过并强制下架资源`;
            },
          })
        }
      >
        通过并强制下架
      </button>
    </>
  );
}

function renderAppealActions(item: AdminAppeal, openModal: (modal: ModalState) => void, load: () => Promise<void>) {
  if (!statusIn(item, 'PENDING', '待审核')) {
    return <span className="muted">已处理</span>;
  }
  return (
    <>
      <button
        className="btn pass"
        onClick={() =>
          openModal({
            title: '填写申诉通过结果',
            placeholder: '请输入通过原因',
            confirmText: '通过申诉',
            onConfirm: async (reason) => {
              await handleAppeal(entityId(item), 'APPROVED', requiredReason(reason));
              await load();
              return `${item.id} 已通过申诉`;
            },
          })
        }
      >
        通过
      </button>
      <button
        className="btn reject"
        onClick={() =>
          openModal({
            title: '填写申诉驳回原因',
            placeholder: '请输入驳回原因',
            confirmText: '驳回申诉',
            onConfirm: async (reason) => {
              await handleAppeal(entityId(item), 'REJECTED', requiredReason(reason));
              await load();
              return `${item.id} 已驳回申诉`;
            },
          })
        }
      >
        驳回
      </button>
    </>
  );
}

function updateLevel(
  setConfig: (updater: (config: AdminConfigData) => AdminConfigData) => void,
  index: number,
  key: keyof MemberLevel,
  value: string,
) {
  setConfig((config) => ({
    ...config,
    memberLevels: config.memberLevels.map((level, levelIndex) => (levelIndex === index ? { ...level, [key]: value } : level)),
  }));
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
          <label className="form-item" key={item.key || item.label}>
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
      <button className="btn-save" onClick={onSave}>{buttonText}</button>
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

function TableWrap({ children }: { children: ReactNode }) {
  return <div className="table-wrap">{children}</div>;
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
  const normalizedPageSize = Math.max(1, pageSize);
  const pageCount = Math.max(1, Math.ceil(total / normalizedPageSize));
  const currentPage = Math.min(Math.max(1, page), pageCount);
  const pageItems = paginationItems(currentPage, pageCount);
  const canPrev = currentPage > 1;
  const canNext = currentPage < pageCount;

  const goToPage = (targetPage: number) => {
    const nextPage = Math.min(Math.max(1, targetPage), pageCount);
    if (nextPage !== currentPage) {
      onPageChange?.(nextPage);
    }
  };

  return (
    <div className="page-box" aria-label={ariaLabel}>
      <div className="page-summary">共 {total} 条 / 第 {currentPage} 页 / 共 {pageCount} 页</div>
      <div className="page-actions">
        <button className="page-btn" disabled={!canPrev} onClick={() => goToPage(1)}>
          首页
        </button>
        <button className="page-btn" disabled={!canPrev} onClick={() => goToPage(currentPage - 1)}>
          上一页
        </button>
        {pageItems.map((item) =>
          typeof item === 'number' ? (
            <button key={item} className={item === currentPage ? 'page-btn active' : 'page-btn'} onClick={() => goToPage(item)}>
              {item}
            </button>
          ) : (
            <span key={item} className="page-ellipsis">...</span>
          ),
        )}
        <button className="page-btn" disabled={!canNext} onClick={() => goToPage(currentPage + 1)}>
          下一页
        </button>
        <button className="page-btn" disabled={!canNext} onClick={() => goToPage(pageCount)}>
          末页
        </button>
      </div>
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
  onConfirm: (message?: string) => void;
}) {
  const [text, setText] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const location = useLocation();

  useEffect(() => {
    setText('');
    setError('');
  }, [location.pathname, modal?.title]);

  if (!modal) return null;

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label={modal.title}>
      <div className="modal-box">
        <div className="modal-title">{modal.title}</div>
        <textarea className="modal-textarea" placeholder={modal.placeholder} value={text} onChange={(event) => setText(event.target.value)} />
        {error && <div className="modal-error">{error}</div>}
        <div className="modal-actions">
          <button className="modal-btn cancel" onClick={onClose} disabled={submitting}>取消</button>
          <button
            className="modal-btn confirm"
            disabled={submitting}
            onClick={async () => {
              setSubmitting(true);
              setError('');
              try {
                const result = await modal.onConfirm?.(text);
                onConfirm(result || modal.message);
              } catch (confirmError) {
                setError(errorMessage(confirmError));
              } finally {
                setSubmitting(false);
              }
            }}
          >
            {submitting ? '提交中...' : modal.confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}

function CatalogEntityModal({
  mode,
  options,
  onClose,
  onSubmit,
}: {
  mode: CatalogModalState;
  options: AdminCatalogOptions;
  onClose: () => void;
  onSubmit: (payload: {
    mode: Exclude<CatalogModalState, null>;
    name: string;
    parentId?: RawId;
    sortOrder?: number;
  }) => Promise<void>;
}) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState('');
  const [sortOrder, setSortOrder] = useState('0');
  const [selectedTag, setSelectedTag] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setName('');
    setParentId('');
    setSortOrder('0');
    setSelectedTag('');
    setError('');
  }, [mode]);

  if (!mode) return null;

  const enabledParents = options.firstLevelCategories.filter((item) => item.status === 'ENABLED');
  const candidates = availableTagCandidates(options);
  const title = mode === 'first-category' ? '新增一级分类' : mode === 'second-category' ? '新增二级分类' : '新增标签';
  const canSubmit = mode === 'tag' ? Boolean(selectedTag) : Boolean(name.trim()) && (mode === 'first-category' || Boolean(parentId));

  const submit = async () => {
    if (!canSubmit || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      await onSubmit({
        mode,
        name: mode === 'tag' ? selectedTag : name.trim(),
        parentId: mode === 'second-category' ? parentId : undefined,
        sortOrder: Number.isFinite(Number(sortOrder)) ? Number(sortOrder) : 0,
      });
    } catch (submitError) {
      setError(errorMessage(submitError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal-box catalog-modal">
        <div className="modal-title">{title}</div>
        {mode === 'second-category' && (
          <label className="modal-field">
            <span>所属一级分类</span>
            <select value={parentId} onChange={(event) => setParentId(event.target.value)}>
              <option value="">请选择启用的一级分类</option>
              {enabledParents.map((item) => (
                <option key={String(item.id)} value={String(item.id)}>{item.name}</option>
              ))}
            </select>
          </label>
        )}
        {mode === 'tag' ? (
          <label className="modal-field">
            <span>规范标签</span>
            <select value={selectedTag} onChange={(event) => setSelectedTag(event.target.value)}>
              <option value="">请选择一级分类、二级分类或资源类型</option>
              {candidates.map((candidate) => (
                <option key={`${candidate.source}-${candidate.name}`} value={candidate.name}>
                  {candidate.name}（{candidate.source}{candidate.exists ? '，已存在' : ''}）
                </option>
              ))}
            </select>
          </label>
        ) : (
          <>
            <label className="modal-field">
              <span>分类名称</span>
              <input value={name} maxLength={60} onChange={(event) => setName(event.target.value)} placeholder="请输入分类名称" />
            </label>
            <label className="modal-field">
              <span>排序值</span>
              <input type="number" min="0" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)} />
            </label>
          </>
        )}
        {mode === 'tag' && candidates.length === 0 && <div className="modal-help">当前规范词均已存在标签，可使用一键补齐刷新状态。</div>}
        {mode === 'second-category' && enabledParents.length === 0 && <div className="modal-help">暂无启用的一级分类，请先新增或启用一级分类。</div>}
        {error && <div className="modal-error">{error}</div>}
        <div className="modal-actions">
          <button className="modal-btn cancel" onClick={onClose} disabled={submitting}>取消</button>
          <button className="modal-btn confirm" onClick={submit} disabled={!canSubmit || submitting}>
            {submitting ? '提交中...' : '提交'}
          </button>
        </div>
      </div>
    </div>
  );
}

function LoadingState() {
  return <div className="state-box">数据加载中...</div>;
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="state-box error-state">
      <span>{message}</span>
      <button className="btn-search" onClick={onRetry}>重试</button>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`status-badge ${statusClass(status)}`}>{status || '-'}</span>;
}

function EmptyRow({ colSpan, show }: { colSpan: number; show: boolean }) {
  if (!show) return null;
  return (
    <tr>
      <td colSpan={colSpan} className="empty-cell">暂无符合条件的数据</td>
    </tr>
  );
}

function emptyPage<T>(size = ADMIN_PAGE_SIZE): PageResult<T> {
  return { total: 0, list: [], items: [], page: 1, size, pageSize: size };
}

function rowsOf<T>(result: PageResult<T>): T[] {
  return result.items || result.list || [];
}

const catalogFilterItems: Array<{ key: CatalogFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'level1', label: '一级分类' },
  { key: 'level2', label: '二级分类' },
  { key: 'tag', label: '标签' },
];

function catalogQuery(filter: CatalogFilter): { section: AdminCatalogSection; params: Record<string, string> } {
  if (filter === 'level1') return { section: 'category', params: { level: '1' } };
  if (filter === 'level2') return { section: 'category', params: { level: '2' } };
  if (filter === 'tag') return { section: 'tag', params: {} };
  return { section: 'all', params: {} };
}

function emptyCatalogOptions(): AdminCatalogOptions {
  return {
    firstLevelCategories: [],
    secondLevelCategories: [],
    resourceTypes: [],
    tagCandidates: [],
    missingTags: [],
  };
}

function availableTagCandidates(options: AdminCatalogOptions): AdminCatalogTagCandidate[] {
  return options.tagCandidates.filter((item) => item.status !== 'ENABLED');
}

function paginationItems(page: number, pageCount: number): Array<number | string> {
  if (pageCount <= 7) {
    return Array.from({ length: pageCount }, (_, index) => index + 1);
  }
  const pages = new Set([1, pageCount, page - 1, page, page + 1]);
  const sortedPages = Array.from(pages)
    .filter((item) => item >= 1 && item <= pageCount)
    .sort((left, right) => left - right);
  const result: Array<number | string> = [];
  sortedPages.forEach((item, index) => {
    const previous = sortedPages[index - 1];
    if (previous && item - previous > 1) {
      result.push(`ellipsis-${previous}-${item}`);
    }
    result.push(item);
  });
  return result;
}

function entityId(item: { id: string; rawId?: RawId; rawTargetId?: RawId; rawResourceId?: RawId }) {
  return item.rawId || item.rawTargetId || item.rawResourceId || item.id;
}

function statusIn(item: { status?: string; rawStatus?: string }, ...statuses: string[]) {
  return statuses.includes(item.rawStatus || '') || statuses.includes(item.status || '');
}

function isTag(item: AdminCategory) {
  return item.kind === 'TAG' || item.type === '标签';
}

function requiredReason(reason: string) {
  return reason.trim() || '管理员操作';
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

function reportActionLabel(action: AdminReportAction) {
  const labels: Record<AdminReportAction, string> = {
    'delete-comment': '删除评论',
    'offline-resource': '下架资源',
    'copyright-down-resource': '版权下架',
    'close-request': '关闭帖子',
    'delete-reply': '删除回复',
    'disable-user': '禁用用户',
  };
  return labels[action] || '处理';
}

function statusClass(status: string) {
  if (['正常', '启用', '已发布', '已通过', '已处理'].includes(status)) return 'ok';
  if (['待审核', '待处理', '处理中', '风险复核'].includes(status)) return 'pending';
  if (['已禁用', '已下架', '版权下架', '已删除', '已驳回', '已关闭'].includes(status)) return 'danger';
  return '';
}

function operationLabel(type: string) {
  const labels: Record<string, string> = {
    ACCOUNT_LOGIN: '账号登录',
    RESOURCE_APPROVE: '资源审核',
    RESOURCE_REJECT: '资源驳回',
    RESOURCE_OFFLINE: '资源下架',
    RESOURCE_RESTORE: '资源恢复',
    RESOURCE_COPYRIGHT_DOWN: '版权下架',
    RESOURCE_DELETE: '资源删除',
    MEMBER_DISABLED: '用户禁用',
    MEMBER_NORMAL: '用户恢复',
    COMMENT_HIDDEN: '评论隐藏',
    COMMENT_DELETED: '评论删除',
    COMMENT_ACTIVE: '评论恢复',
    REPORT_HANDLE: '举报处理',
    APPEAL_HANDLE: '申诉处理',
    CATEGORY_CREATE: '分类新增',
    CATEGORY_UPDATE: '分类修改',
    CATEGORY_ENABLE: '分类启用',
    CATEGORY_DISABLE: '分类禁用',
    TAG_BACKFILL: '标签补齐',
    TAG_CREATE: '标签新增',
    TAG_UPDATE: '标签修改',
    TAG_ENABLE: '标签启用',
    TAG_DISABLE: '标签禁用',
    TAG_MERGE: '标签合并',
    MEMBER_LEVEL_UPDATE: '等级配置',
    SYSTEM_CONFIG_UPDATE: '系统参数',
    CACHE_REFRESH: '缓存刷新',
  };
  return labels[type] || type;
}

function targetLabel(type: string) {
  const labels: Record<string, string> = {
    RESOURCE: '共享资源',
    MEMBER: '平台用户',
    COMMENT: '用户评论',
    REPORT_COMPLAINT: '举报投诉单',
    APPEAL: '申诉单',
    CATEGORY: '资源分类',
    TAG: '资源标签',
    MEMBERSHIP_LEVEL: '会员等级',
    SYSTEM_CONFIG: '系统配置',
  };
  return labels[type] || type;
}

function snapshotText(value: unknown) {
  if (value == null || value === '') return '-';
  const text = String(value);
  try {
    const parsed = JSON.parse(text);
    return parsed.status || text;
  } catch {
    return text;
  }
}
