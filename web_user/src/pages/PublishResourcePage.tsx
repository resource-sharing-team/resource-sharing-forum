import { InboxOutlined, SendOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, Row, Select, Upload, message } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePublishResource } from '../api/hooks';
import { categories, resourceTypes } from '../data/catalog';
import { resourcePublishSchema } from '../utils/validation';

export default function PublishResourcePage() {
  const [form] = Form.useForm();
  const [files, setFiles] = useState<UploadFile[]>([]);
  const navigate = useNavigate();
  const publish = usePublishResource();
  const category1 = Form.useWatch('category1', form);
  const selectedCategory = categories.find((item) => item.id === category1);

  return (
    <>
      <div className="section-head">
        <div>
          <p className="section-kicker">PUBLISH</p>
          <h1 className="section-title">发布资源</h1>
        </div>
      </div>
      <div className="detail-hero">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ tags: [], points: 0 }}
          onFinish={async (values) => {
            const parsed = resourcePublishSchema.safeParse(values);
            if (!parsed.success) {
              message.error(parsed.error.issues[0]?.message || '表单校验失败');
              return;
            }
            const formData = new FormData();
            Object.entries(parsed.data).forEach(([key, value]) => {
              formData.append(key, Array.isArray(value) ? value.join(',') : String(value));
            });
            formData.append('fileName', files[0]?.name || 'resource-file.zip');
            files.forEach((file) => {
              if (file.originFileObj) formData.append('files', file.originFileObj);
            });
            const resource = await publish.mutateAsync(formData);
            message.success('资源已提交审核');
            navigate(`/resources/${resource.id}`);
          }}
        >
          <Form.Item name="title" label="资源标题" rules={[{ required: true, message: '请输入资源标题' }]}>
            <Input placeholder="5-80 字，清楚描述资源内容" />
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
                  placeholder="请先选择一级分类"
                  disabled={!selectedCategory}
                  options={selectedCategory?.children.map((item) => ({ value: item.id, label: item.name })) || []}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item name="type" label="资源类型" rules={[{ required: true, message: '请选择资源类型' }]}>
                <Select options={resourceTypes.map((item) => ({ value: item, label: item }))} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="tags" label="资源标签" rules={[{ required: true, message: '请添加标签' }]}>
                <Select mode="tags" maxCount={5} placeholder="输入标签后回车，最多 5 个" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="资源简介" rules={[{ required: true, message: '请输入资源简介' }]}>
            <Input.TextArea rows={3} placeholder="20-200 字，说明资源内容、用途、亮点" />
          </Form.Item>
          <Form.Item name="detail" label="详细说明" rules={[{ required: true, message: '请输入详细说明' }]}>
            <Input.TextArea rows={6} placeholder="介绍资源内容、使用方法、适用人群、注意事项" />
          </Form.Item>

          <Form.Item label="上传附件">
            <Upload.Dragger
              multiple
              maxCount={5}
              fileList={files}
              beforeUpload={() => false}
              onChange={({ fileList }) => setFiles(fileList)}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">选择或拖拽资源附件，可一次上传多个</p>
              <p className="ant-upload-hint">最多 5 个附件；详情页会按附件逐个展示下载入口。</p>
            </Upload.Dragger>
          </Form.Item>

          <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={publish.isPending}>
            提交审核
          </Button>
          <Button style={{ marginLeft: 10 }} onClick={() => navigate(-1)}>
            取消
          </Button>
        </Form>
      </div>
    </>
  );
}
