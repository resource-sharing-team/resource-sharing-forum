import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Form, Input, Space, Typography, message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { login, register, resetPassword } from '../api/endpoints';
import { useAuthStore } from '../store/auth';

type AuthMode = 'login' | 'register' | 'forgot';

const copy: Record<AuthMode, { title: string; subtitle: string; action: string }> = {
  login: { title: '欢迎回来', subtitle: '登录后可以发布、收藏、评论和管理个人资料。', action: '登录' },
  register: { title: '创建账号', subtitle: '用一个账号沉淀你的资源贡献和求资源记录。', action: '注册' },
  forgot: { title: '重置密码', subtitle: '通过邮箱验证码完成密码重置。', action: '重置密码' },
};

export default function AuthPage({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { setSession } = useAuthStore();
  const returnTo = typeof location.state === 'object' && location.state && 'from' in location.state ? String(location.state.from || '/') : '/';
  const mutation = useMutation({
    mutationFn: async (values: Record<string, string>) => {
      if (mode === 'login') return login({ account: values.account, password: values.password });
      if (mode === 'register') return register({ username: values.username, email: values.email, password: values.password });
      await resetPassword({ email: values.email, code: values.code, password: values.password });
      return null;
    },
    onSuccess: (result) => {
      if (result) {
        setSession(result.token, result.user);
        message.success(`${copy[mode].action}成功`);
        navigate(returnTo);
      } else {
        message.success('密码已重置，请重新登录');
        navigate('/login');
      }
    },
  });

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-art">
          <Typography.Title style={{ color: 'white', marginTop: 0 }}>资源分享论坛</Typography.Title>
          <Typography.Paragraph style={{ color: 'rgba(255,255,255,0.82)', fontSize: 16, lineHeight: 1.8 }}>
            用户端已迁移到 React/Vite 架构。登录注册统一请求 `/api/auth/*`，可按环境配置连接真实后端或本地 mock。
          </Typography.Paragraph>
          <Space direction="vertical" size={12} style={{ marginTop: 24 }}>
            <span>统一会话状态：Zustand</span>
            <span>统一接口请求：Axios</span>
            <span>统一服务端缓存：TanStack Query</span>
          </Space>
        </div>
        <div className="auth-form">
          <Typography.Title level={2} style={{ marginTop: 0 }}>{copy[mode].title}</Typography.Title>
          <Typography.Paragraph type="secondary">{copy[mode].subtitle}</Typography.Paragraph>
          <Form layout="vertical" onFinish={(values) => mutation.mutate(values)}>
            {mode === 'login' && (
              <Form.Item name="account" label="账号" rules={[{ required: true, message: '请输入用户名或邮箱' }]}>
                <Input prefix={<UserOutlined />} placeholder="用户名或邮箱" />
              </Form.Item>
            )}
            {mode === 'register' && (
              <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }, { min: 2, message: '至少 2 个字符' }]}>
                <Input prefix={<UserOutlined />} placeholder="2-20 个字符" />
              </Form.Item>
            )}
            {(mode === 'register' || mode === 'forgot') && (
              <Form.Item name="email" label="邮箱" rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]}>
                <Input prefix={<MailOutlined />} placeholder="name@example.com" />
              </Form.Item>
            )}
            {mode === 'forgot' && (
              <Form.Item name="code" label="邮箱验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                <Input placeholder="6 位数字验证码" />
              </Form.Item>
            )}
            <Form.Item name="password" label={mode === 'forgot' ? '新密码' : '密码'} rules={[{ required: true, message: '请输入密码' }, { min: 8, message: '至少 8 位' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="至少 8 位，建议包含字母和数字" />
            </Form.Item>
            {mode !== 'login' && (
              <Form.Item
                name="confirm"
                label="确认密码"
                dependencies={['password']}
                rules={[
                  { required: true, message: '请再次输入密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次密码不一致'));
                    },
                  }),
                ]}
              >
                <Input.Password prefix={<LockOutlined />} placeholder="再次输入密码" />
              </Form.Item>
            )}
            <Button type="primary" block htmlType="submit" loading={mutation.isPending}>
              {copy[mode].action}
            </Button>
          </Form>
          <Space style={{ marginTop: 18 }}>
            {mode !== 'login' && <Link to="/login">返回登录</Link>}
            {mode !== 'register' && <Link to="/register">注册账号</Link>}
            {mode !== 'forgot' && <Link to="/forgot-password">忘记密码</Link>}
            <Link to="/">先看看资源</Link>
          </Space>
        </div>
      </section>
    </main>
  );
}
