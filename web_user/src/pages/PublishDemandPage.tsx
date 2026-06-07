import { SendOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, InputNumber, Row, Select, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { usePublishDemand } from '../api/hooks';
import { categories } from '../data/catalog';
import { demandPublishSchema } from '../utils/validation';

export default function PublishDemandPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const publish = usePublishDemand();
  const category1 = Form.useWatch('category1', form);
  const selectedCategory = categories.find((item) => item.id === category1);

  return (
    <>
      <div className="section-head">
        <div>
          <p className="section-kicker">REQUEST</p>
          <h1 className="section-title">发布求资源</h1>
        </div>
      </div>
      <div className="detail-hero">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ points: 0, tags: [] }}
          onFinish={async (values) => {
            const parsed = demandPublishSchema.safeParse(values);
            if (!parsed.success) {
              message.error(parsed.error.issues[0]?.message || '表单校验失败');
              return;
            }
            const demand = await publish.mutateAsync(parsed.data);
            message.success('求资源已发布');
            navigate(`/demands/${demand.id}`);
          }}
        >
          <Form.Item name="title" label="求资源标题" rules={[{ required: true, message: '请输入标题' }]}>
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
            <Col xs={24} md={12}>
              <Form.Item name="points" label="悬赏积分">
                <InputNumber min={0} max={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="tags" label="需求标签" rules={[{ required: true, message: '请添加标签' }]}>
            <Select mode="tags" maxCount={5} placeholder="输入标签后回车，最多 5 个" />
          </Form.Item>
          <Form.Item name="description" label="需求说明" rules={[{ required: true, message: '请输入需求说明' }]}>
            <Input.TextArea rows={7} placeholder="20-500 字，说明用途、版本要求、格式、是否接受替代资源" />
          </Form.Item>
          <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={publish.isPending}>
            发布需求
          </Button>
          <Button style={{ marginLeft: 10 }} onClick={() => navigate(-1)}>
            取消
          </Button>
        </Form>
      </div>
    </>
  );
}
