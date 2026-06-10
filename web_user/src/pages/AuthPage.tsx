import { message } from 'antd';
import { useMutation } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { login, register, resetPassword, sendResetPasswordCode } from '../api/endpoints';
import { useAuthStore } from '../store/auth';

type AuthMode = 'login' | 'register' | 'forgot';

export default function AuthPage({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate();
  const { setSession } = useAuthStore();
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const mutation = useMutation({
    mutationFn: async () => {
      if (mode === 'login') return login({ account: values.account, password: values.password });
      if (mode === 'register') return register({ username: values.username, email: values.email, password: values.password });
      await resetPassword({ email: values.email, code: values.code, password: values.password });
      return null;
    },
    onSuccess: (result) => {
      if (result) {
        setSession(result.token, result.user);
        message.success(mode === 'login' ? '登录成功' : '注册成功');
        navigate('/');
      } else {
        message.success('密码已重置，请重新登录');
        navigate('/login');
      }
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '接口调用失败'),
  });
  const codeMutation = useMutation({
    mutationFn: () => sendResetPasswordCode({ email: values.email }),
    onSuccess: (result) => {
      message.success(result.devCode ? `校验码：${result.devCode}` : '校验码已发送');
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '接口调用失败'),
  });

  const update = (key: string, value: string) => setValues((prev) => ({ ...prev, [key]: value }));

  function validate() {
    const next: Record<string, string> = {};
    if (mode === 'login') {
      if (!values.account) next.account = '请输入用户名或邮箱';
      if (!values.password) next.password = '请输入密码';
    }
    if (mode === 'register') {
      if (!values.username || values.username.length < 2) next.username = '用户名长度需为2-20个字符';
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email || '')) next.email = '邮箱格式不正确';
      if (!values.password || values.password.length < 8) next.password = '密码长度需为8-16位';
      if (values.password !== values.confirm) next.confirm = '两次输入的密码不一致';
    }
    if (mode === 'forgot') {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email || '')) next.email = '邮箱格式不正确';
      if (!/^\d{6}$/.test(values.code || '')) next.code = '验证码必须为6位数字';
      if (!values.password || values.password.length < 8) next.password = '密码长度需为8-16位';
      if (values.password !== values.confirm) next.confirm = '两次输入的密码不一致';
    }
    setErrors(next);
    return !Object.keys(next).length;
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (validate()) mutation.mutate();
  }

  function sendCode() {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email || '')) {
      setErrors((prev) => ({ ...prev, email: '邮箱格式不正确' }));
      return;
    }
    codeMutation.mutate();
  }

  const wrapper = mode === 'login' ? 'login-wrap' : mode === 'register' ? 'reg-wrap' : 'forgot-wrap';
  const title = mode === 'login' ? '资源分享论坛' : mode === 'register' ? '用户注册' : '找回密码';

  return (
    <main className="auth-page">
      <div className={wrapper}>
        <div className="auth-title">{title}</div>
        <form onSubmit={submit}>
          {mode === 'login' && (
            <>
              <AuthInput label="用户名/邮箱" value={values.account} error={errors.account} placeholder="请输入用户名或邮箱" onChange={(value) => update('account', value)} />
              <AuthInput label="密码" type="password" value={values.password} error={errors.password} placeholder="请输入登录密码" onChange={(value) => update('password', value)} />
            </>
          )}

          {mode === 'register' && (
            <>
              <AuthInput label="用户名" value={values.username} error={errors.username} placeholder="2-20个字符，支持中文、英文、数字和下划线" onChange={(value) => update('username', value)} />
              <AuthInput label="邮箱" type="email" value={values.email} error={errors.email} placeholder="请输入注册邮箱" onChange={(value) => update('email', value)} />
              <AuthInput label="设置密码" type="password" value={values.password} error={errors.password} placeholder="8-16位，需同时包含字母和数字" onChange={(value) => update('password', value)} />
              <AuthInput label="确认密码" type="password" value={values.confirm} error={errors.confirm} placeholder="再次输入密码" onChange={(value) => update('confirm', value)} />
            </>
          )}

          {mode === 'forgot' && (
            <>
              <AuthInput label="绑定邮箱" type="email" value={values.email} error={errors.email} placeholder="请输入注册时绑定的邮箱" onChange={(value) => update('email', value)} />
              <CodeInput value={values.code} error={errors.code} onChange={(value) => update('code', value)} onSend={sendCode} pending={codeMutation.isPending} />
              <AuthInput label="新密码" type="password" value={values.password} error={errors.password} placeholder="8-16位，需同时包含字母和数字" onChange={(value) => update('password', value)} />
              <AuthInput label="确认新密码" type="password" value={values.confirm} error={errors.confirm} placeholder="再次输入新密码" onChange={(value) => update('confirm', value)} />
            </>
          )}

          <button className="auth-submit" type="submit" disabled={mutation.isPending}>
            {mode === 'login' ? '立即登录' : mode === 'register' ? '提交注册' : '重置密码'}
          </button>
        </form>

        <div className="tip-link">
          {mode === 'login' && (
            <>
              <Link to="/forgot-password">忘记密码？</Link>
              &nbsp;&nbsp;|&nbsp;&nbsp;
              <Link to="/register">前往注册</Link>
            </>
          )}
          {mode === 'register' && <>已有账号？<Link to="/login">返回登录</Link></>}
          {mode === 'forgot' && <>想起来了？<Link to="/login">返回登录</Link></>}
        </div>
      </div>
    </main>
  );
}

function AuthInput({ label, value = '', error, placeholder, type = 'text', onChange }: { label: string; value?: string; error?: string; placeholder: string; type?: string; onChange: (value: string) => void }) {
  return (
    <div className="form-item">
      <label>{label}</label>
      <input className="form-input" type={type} value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />
      <div className="error-text">{error}</div>
    </div>
  );
}

function CodeInput({ value = '', error, pending, onChange, onSend }: { value?: string; error?: string; pending?: boolean; onChange: (value: string) => void; onSend: () => void }) {
  return (
    <div className="form-item">
      <label>邮箱验证码</label>
      <div className="input-row">
        <input className="form-input" value={value} placeholder="请输入6位数字验证码" onChange={(event) => onChange(event.target.value)} />
        <button type="button" className="code-btn" onClick={onSend} disabled={pending}>
          获取验证码
        </button>
      </div>
      <div className="error-text">{error}</div>
    </div>
  );
}
