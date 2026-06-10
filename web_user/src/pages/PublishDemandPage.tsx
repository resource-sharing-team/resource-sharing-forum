import { SendOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, InputNumber, Radio, Row, Select, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useMe, usePublishDemand } from '../api/hooks';
import { categories } from '../data/catalog';
import { demandPublishSchema } from '../utils/validation';

export default function PublishDemandPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const meQuery = useMe();
  const publish = usePublishDemand();
  const category1 = Form.useWatch('category1', form);
  const rewardType = Form.useWatch('rewardType', form) || 'FREE';
  const selectedCategory = categories.find((item) => item.id === category1);
  const user = meQuery.data;
  const points = Number(user?.points || 0);
  const frozenPoints = Number(user?.frozenPoints || 0);
  const availablePoints = Number(user?.availablePoints ?? Math.max(0, points - frozenPoints));
  const rewardLimit = Number(user?.rewardLimit ?? availablePoints);
  const maxRewardPoints = Math.max(0, Math.min(availablePoints, rewardLimit));

  return (
    <div className="narrow-page">
      <div className="card form-card">
        <div className="card-title">发布求资源</div>
        <div className="card-body">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ rewardType: 'FREE', rewardPoints: 0, tags: [] }}
          onFinish={async (values) => {
            const parsed = demandPublishSchema.safeParse(values);
            if (!parsed.success) {
              message.error(parsed.error.issues[0]?.message || '表单校验失败');
              return;
            }
            const requestedPoints = parsed.data.rewardType === 'POINT' ? Number(parsed.data.rewardPoints || 0) : 0;
            if (parsed.data.rewardType === 'POINT' && requestedPoints > maxRewardPoints) {
              message.error('悬赏积分不能超过可用积分或会员等级上限');
              return;
            }
            try {
              const demand = await publish.mutateAsync({
                ...parsed.data,
                rewardType: parsed.data.rewardType || 'FREE',
                rewardPoints: requestedPoints,
                points: requestedPoints,
              });
              message.success('求资源已发布');
              navigate(`/demands/${demand.id}`);
            } catch (error) {
              message.error(error instanceof Error ? error.message : '发布失败，请稍后重试');
            }
          }}
        >
          <Form.Item name="title" label="求资源标题" extra="例：求 2026 教资面试真题、求 Python 实战项目源码" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="5-80 字，清楚描述你需要的资源" />
          </Form.Item>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item name="category1" label="一级分类" rules={[{ required: true, message: '请选择一级分类' }]}>
                <Select
                  placeholder="请选择"
                  options={categories.map((item) => ({ value: item.id, label: item.name }))}
                  onChange={() => form.setFieldValue('category2', undefined)}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="category2" label="二级分类" rules={[{ required: true, message: '请选择二级分类' }]}>
                <Select
                  disabled={!selectedCategory}
                  placeholder="请先选择一级分类"
                  options={selectedCategory?.children.map((item) => ({ value: item.id, label: item.name })) || []}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item name="format" label="期望格式" rules={[{ required: true, message: '请填写期望格式' }]}>
                <Input placeholder="例如 PDF、源码、视频、Figma" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="tags" label="需求标签" rules={[{ required: true, message: '请添加标签' }]}>
            <Select mode="tags" maxCount={5} placeholder="输入标签后回车，最多 5 个" />
          </Form.Item>
          <Form.Item name="description" label="需求说明" rules={[{ required: true, message: '请输入需求说明' }]}>
            <Input.TextArea rows={7} placeholder="20-500 字，说明用途、版本要求、格式、是否接受替代资源" />
          </Form.Item>
          <Form.Item name="rewardType" label="悬赏设置">
            <Radio.Group
              onChange={(event) => {
                form.setFieldValue('rewardPoints', event.target.value === 'POINT' && maxRewardPoints >= 1 ? 1 : 0);
              }}
            >
              <Radio value="FREE">免费求资源</Radio>
              <Radio value="POINT">积分悬赏</Radio>
            </Radio.Group>
          </Form.Item>
          {rewardType === 'POINT' && (
            <Form.Item name="rewardPoints" label="悬赏积分" rules={[{ required: true, message: '请输入悬赏积分' }]}>
              <InputNumber min={1} max={Math.max(1, maxRewardPoints)} disabled={maxRewardPoints < 1} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <div className="tip">
            可用积分 {availablePoints}，本次最高可悬赏 {maxRewardPoints}。积分悬赏将冻结对应积分，采纳后发放给回答者。
          </div>
          <div className="btn-bar">
            <Button onClick={() => navigate(-1)}>取消</Button>
            <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={publish.isPending}>
              发布求资源
            </Button>
          </div>
          <div className="tip" style={{ textAlign: 'center', marginTop: 12 }}>
            发布后可在个人中心查看，未采纳前可取消
          </div>
        </Form>
        </div>
      </div>
    </div>
  );
}
