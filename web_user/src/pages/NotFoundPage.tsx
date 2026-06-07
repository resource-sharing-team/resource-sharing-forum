import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="这个路由还没有被定义。"
      extra={<Button type="primary" onClick={() => navigate('/')}>回到首页</Button>}
    />
  );
}
