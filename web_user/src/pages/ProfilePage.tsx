import {
  BellOutlined,
  EditOutlined,
  HeartOutlined,
  LockOutlined,
  MailOutlined,
  SafetyOutlined,
  StarOutlined,
  TrophyOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  Form,
  Input,
  List,
  Pagination,
  Progress,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import { useState, type ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useBindEmail, useChangePassword, useMe, usePointAccount, usePointFlows, useProfileSummary, useUpdateMe } from '../api/hooks';
import { useAuthStore } from '../store/auth';
import type { User } from '../types';

export default function ProfilePage() {
  const location = useLocation();
  const meQuery = useMe();
  const summaryQuery = useProfileSummary();
  const updateMe = useUpdateMe();
  const changePassword = useChangePassword();
  const bindEmail = useBindEmail();
  const token = useAuthStore((state) => state.token);
  const cachedUser = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const [activeTab, setActiveTab] = useState('profile');
  const user = meQuery.data || cachedUser;

  if (!token && !user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (meQuery.isLoading && !user) return <Spin fullscreen />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  const summary = summaryQuery.data;
  const unreadCount = summary?.messages.filter((item) => item.unread).length || 0;
  const expNeeded = Math.max(1, Number(user.expNeeded || 1));
  const expPercent = Number(user.progressPercent ?? user.upgradeProgress ?? Math.min(100, Math.round((Number(user.points || 0) / expNeeded) * 100)));

  const profileSections: Array<{ key: string; label: ReactNode; children: ReactNode }> = [
    {
      key: 'profile',
      label: (
        <span>
          <EditOutlined /> 个人资料
        </span>
      ),
      children: (
        <div className="detail-hero form-card">
          <Form
            layout="vertical"
            initialValues={user}
            onFinish={async (values) => {
              const next = await updateMe.mutateAsync(values);
              setUser(next);
              message.success('资料已保存');
            }}
          >
            <Form.Item label="用户名" name="username">
              <Input disabled />
            </Form.Item>
            <Form.Item label="昵称" name="nickname" rules={[{ required: true, message: '请输入昵称' }]}>
              <Input maxLength={20} />
            </Form.Item>
            <Form.Item label="简介" name="bio">
              <Input.TextArea rows={4} maxLength={100} showCount />
            </Form.Item>
            <Form.Item label="联系方式" name="contact">
              <Input placeholder="邮箱 / QQ / 微信，仅管理员可见" />
            </Form.Item>
            <Form.Item label="头像地址" name="avatar">
              <Input prefix={<UploadOutlined />} />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={updateMe.isPending}>
              保存修改
            </Button>
          </Form>
        </div>
      ),
    },
    {
      key: 'my-resource',
      label: <span>我发布的资源</span>,
      children: (
        <ProfileList
          title="我发布的资源"
          data={summary?.resources || []}
          render={(item) => <Link to={`/resources/${item.id}`}>{item.title}</Link>}
          meta={(item) => `${item.date} / ${item.type} / 下载 ${item.downloads}`}
        />
      ),
    },
    {
      key: 'my-demand',
      label: <span>我的求资源</span>,
      children: (
        <ProfileList
          title="我的求资源"
          data={summary?.demands || []}
          render={(item) => <Link to={`/demands/${item.id}`}>{item.title}</Link>}
          meta={(item) => `${item.date} / ${item.status === 'solved' ? '已解决' : '进行中'} / 回答 ${item.replyCount}`}
        />
      ),
    },
    {
      key: 'my-fav',
      label: (
        <span>
          <StarOutlined /> 我的收藏
        </span>
      ),
      children: (
        <ProfileList
          title="我的收藏"
          data={summary?.favorites || []}
          render={(item) => <Link to={`/resources/${item.id}`}>{item.title}</Link>}
          meta={(item) => `${item.date} / ${item.author}`}
        />
      ),
    },
    {
      key: 'my-like',
      label: (
        <span>
          <HeartOutlined /> 我的点赞
        </span>
      ),
      children: (
        <ProfileList
          title="我的点赞"
          data={summary?.likes || []}
          render={(item) => <Link to={`/resources/${item.id}`}>{item.title}</Link>}
          meta={(item) => `${item.date} / ${item.author}`}
        />
      ),
    },
    {
      key: 'member',
      label: (
        <span>
          <TrophyOutlined /> 会员中心
        </span>
      ),
      children: <MemberCenter user={user} />,
    },
    {
      key: 'message',
      label: (
        <span>
          <BellOutlined /> 消息中心
          {unreadCount > 0 && <Badge className="tab-badge" count={unreadCount} size="small" />}
        </span>
      ),
      children: (
        <ProfileList
          title="站内消息"
          data={summary?.messages || []}
          render={(item) => (
            <Space>
              {item.unread && <Tag color="red">未读</Tag>}
              {item.title}
            </Space>
          )}
          meta={(item) => `${item.date} / ${item.content}`}
        />
      ),
    },
    {
      key: 'security',
      label: (
        <span>
          <LockOutlined /> 安全中心
        </span>
      ),
      children: (
        <SecurityCenter
          email={user.email}
          emailVerified={user.emailVerified}
          passwordUpdatedAt={user.passwordUpdatedAt}
          loginLogs={summary?.loginLogs || []}
          onChangePassword={async (values) => {
            await changePassword.mutateAsync(values);
            message.success('密码已更新');
          }}
          onBindEmail={async (values) => {
            const next = await bindEmail.mutateAsync(values);
            setUser(next);
            message.success('邮箱已绑定');
          }}
          passwordLoading={changePassword.isPending}
          emailLoading={bindEmail.isPending}
        />
      ),
    },
    {
      key: 'login-log',
      label: <span>登录记录</span>,
      children: (
        <ProfileList
          title="登录记录"
          data={summary?.loginLogs || []}
          render={(item) => `${item.device} - ${item.location}`}
          meta={(item) => `${item.time} / IP ${item.ip}`}
        />
      ),
    },
  ];
  const activeSection = profileSections.find((item) => item.key === activeTab) || profileSections[0];

  return (
    <div className="main-wrapper profile-wrapper">
      <aside className="left-menu">
        <div className="card">
          <div className="card-body">
            <div className="user-info">
              <Avatar className="user-avatar" size={60} src={user.avatar || undefined}>
                {user.nickname.slice(0, 1)}
              </Avatar>
              <div className="user-text">
                <h3>{user.nickname}</h3>
                <p>{user.level} | 积分：{user.points}</p>
                <div className="level-box">
                  <div className="level-text">
                    <span>升级进度</span>
                    <span>{user.points}/{user.expNeeded}</span>
                  </div>
                  <Progress percent={expPercent} showInfo={false} strokeColor="#2e7d32" trailColor="#eee" size="small" />
                </div>
              </div>
            </div>
          </div>
        </div>
        {profileSections.map((item) => (
          <button type="button" className={activeTab === item.key ? 'menu-item active' : 'menu-item'} key={item.key} onClick={() => setActiveTab(item.key)}>
            {item.label}
          </button>
        ))}
      </aside>
      <section className="right-content">
        <div className="tab-content active">{activeSection.children}</div>
      </section>
    </div>
  );
}

function MemberCenter({ user }: { user: User }) {
  const [flowPage, setFlowPage] = useState(1);
  const flowSize = 8;
  const pointAccountQuery = usePointAccount();
  const pointFlowsQuery = usePointFlows(flowPage, flowSize);
  const account = pointAccountQuery.data || user;
  const points = Number(account.points || 0);
  const frozenPoints = Number(account.frozenPoints || 0);
  const availablePoints = Number(account.availablePoints ?? Math.max(0, points - frozenPoints));
  const expNeeded = Number(account.expNeeded || 0);
  const level = account.level || 'Member';
  const nextLevel = account.nextLevel || '';
  const progress = Number(account.progressPercent ?? account.upgradeProgress ?? (expNeeded > 0 ? Math.min(100, Math.round((points / (points + expNeeded)) * 100)) : 100));
  const rules = account.rules || account.pointRules || [];
  const benefits = account.benefits || [];
  const flows = pointFlowsQuery.data?.items || [];
  const flowTotal = Number(pointFlowsQuery.data?.total || 0);

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      <section className="member-hero">
        <div>
          <p className="section-kicker">MEMBERSHIP</p>
          <Typography.Title level={3} style={{ marginTop: 0 }}>当前等级：{level}</Typography.Title>
          <Progress percent={progress} strokeColor="#2e7d32" trailColor="#eee" />
          <Typography.Paragraph type="secondary">
            当前积分 {points}，冻结积分 {frozenPoints}，可用积分 {availablePoints}。
            {nextLevel ? ` 距离 ${nextLevel} 还需 ${expNeeded} 分。` : ' 已达到当前最高等级。'}
          </Typography.Paragraph>
        </div>
        <div className="current-benefits">
          {benefits.length > 0
            ? benefits.map((benefit) => (
              <Tag color="green" key={`${benefit.name}-${String(benefit.limit)}`}>
                {benefit.name}: {String(benefit.limit)}
              </Tag>
            ))
            : <Tag color="default">暂无权益配置</Tag>}
        </div>
      </section>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <Card title="积分规则" className="rule-card">
            <List
              dataSource={rules}
              locale={{ emptyText: '暂无积分规则配置' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta title={<Space><Tag color={item.points.startsWith('+') ? 'green' : 'red'}>{item.points}</Tag>{item.action}</Space>} description={item.note} />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card title="会员权益规则" className="rule-card">
            <List
              dataSource={benefits}
              locale={{ emptyText: '暂无会员权益配置' }}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={<Space><Tag color={item.enabled ? 'green' : 'default'}>{item.name}</Tag><Typography.Text>{String(item.limit)}</Typography.Text></Space>}
                    description={item.description}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Card title="积分流水" className="rule-card">
        <List
          loading={pointFlowsQuery.isLoading}
          dataSource={flows}
          locale={{ emptyText: '暂无积分流水' }}
          renderItem={(item) => {
            const pointChange = Number(item.pointsChange || 0);
            const frozenChange = Number(item.frozenChange || 0);
            const tagColor = pointChange > 0 ? 'green' : pointChange < 0 || frozenChange < 0 ? 'red' : 'blue';
            const changeText = pointChange !== 0
              ? `${pointChange > 0 ? '+' : ''}${pointChange}`
              : `${frozenChange > 0 ? '冻结 +' : '冻结 '}${frozenChange}`;
            return (
              <List.Item>
                <List.Item.Meta
                  title={<Space><Tag color={tagColor}>{changeText}</Tag>{item.sceneLabel}</Space>}
                  description={`${item.description || item.relatedLabel || '积分变动'} / ${item.balanceText || ''} / ${item.createTime}`}
                />
              </List.Item>
            );
          }}
        />
        {flowTotal > flowSize && (
          <Pagination
            size="small"
            current={flowPage}
            pageSize={flowSize}
            total={flowTotal}
            showSizeChanger={false}
            onChange={setFlowPage}
            style={{ marginTop: 12, textAlign: 'right' }}
          />
        )}
      </Card>
    </Space>
  );
}

type SecurityCenterProps = {
  email: string;
  emailVerified: boolean;
  passwordUpdatedAt: string;
  loginLogs: Array<{ id: number; ip: string; device: string; location: string; time: string }>;
  onChangePassword: (values: { oldPassword: string; newPassword: string }) => Promise<void>;
  onBindEmail: (values: { email: string; code: string }) => Promise<void>;
  passwordLoading: boolean;
  emailLoading: boolean;
};

function SecurityCenter({
  email,
  emailVerified,
  passwordUpdatedAt,
  loginLogs,
  onChangePassword,
  onBindEmail,
  passwordLoading,
  emailLoading,
}: SecurityCenterProps) {
  const [passwordForm] = Form.useForm();
  const [emailForm] = Form.useForm();

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card className="security-card" title={<Space><SafetyOutlined /> 修改密码</Space>}>
            <Typography.Paragraph type="secondary">上次修改：{passwordUpdatedAt}</Typography.Paragraph>
            <Form
              form={passwordForm}
              layout="vertical"
              onFinish={async ({ oldPassword, newPassword }) => {
                await onChangePassword({ oldPassword, newPassword });
                passwordForm.resetFields();
              }}
            >
              <Form.Item name="oldPassword" label="当前密码" rules={[{ required: true, message: '请输入当前密码' }]}>
                <Input.Password />
              </Form.Item>
              <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '至少 8 位' }]}>
                <Input.Password />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label="确认新密码"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: '请再次输入新密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      return !value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('两次密码不一致'));
                    },
                  }),
                ]}
              >
                <Input.Password />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={passwordLoading}>更新密码</Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card className="security-card" title={<Space><MailOutlined /> 绑定邮箱</Space>}>
            <Typography.Paragraph type="secondary">
              当前邮箱：{email} <Tag color={emailVerified ? 'blue' : 'orange'}>{emailVerified ? '已验证' : '未验证'}</Tag>
            </Typography.Paragraph>
            <Form
              form={emailForm}
              layout="vertical"
              onFinish={async (values) => {
                await onBindEmail(values);
                emailForm.resetFields(['code']);
              }}
            >
              <Form.Item name="email" label="新邮箱" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
                <Input placeholder="name@example.com" />
              </Form.Item>
              <Form.Item name="code" label="邮箱验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                <Space.Compact style={{ width: '100%' }}>
                  <Input placeholder="6 位验证码" />
                  <Button onClick={() => message.success('验证码已发送，mock 环境可输入任意数字')}>获取验证码</Button>
                </Space.Compact>
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={emailLoading}>绑定邮箱</Button>
            </Form>
          </Card>
        </Col>
      </Row>

      <ProfileList
        title="登录记录"
        data={loginLogs}
        render={(item) => `${item.device} - ${item.location}`}
        meta={(item) => `${item.time} / IP ${item.ip}`}
      />
    </Space>
  );
}

type ProfileListProps<T> = {
  title: string;
  icon?: ReactNode;
  data: T[];
  render: (item: T) => ReactNode;
  meta: (item: T) => ReactNode;
};

function ProfileList<T extends { id: number }>({ title, icon, data, render, meta }: ProfileListProps<T>) {
  return (
    <div className="detail-hero">
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        {icon} {title}
      </Typography.Title>
      <List
        dataSource={data}
        locale={{ emptyText: '暂无数据' }}
        renderItem={(item) => (
          <List.Item>
            <List.Item.Meta title={render(item)} description={meta(item)} />
          </List.Item>
        )}
      />
    </div>
  );
}
