import { SearchOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, Row, Select } from 'antd';
import { categories, resourceTypes } from '../data/catalog';
import type { ListParams } from '../types';

type Props = {
  mode: 'resources' | 'demands';
  value: ListParams;
  onChange: (next: ListParams) => void;
};

export default function ListingFilter({ mode, value, onChange }: Props) {
  const selectedCategory = categories.find((item) => item.id === value.cate1);
  const update = (patch: ListParams) => onChange({ ...value, ...patch, page: 1 });

  return (
    <div className="filter-strip">
      <Form layout="vertical">
        <Row gutter={[12, 12]}>
          <Col xs={24} md={mode === 'resources' ? 7 : 8}>
            <Form.Item label="关键词">
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder={mode === 'resources' ? '标题、标签、发布者' : '需求标题、格式、发布者'}
                value={value.keyword}
                onChange={(event) => update({ keyword: event.target.value })}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={5}>
            <Form.Item label="一级分类">
              <Select
                allowClear
                placeholder="全部"
                value={value.cate1 || undefined}
                options={categories.map((item) => ({ value: item.id, label: item.name }))}
                onChange={(cate1) => update({ cate1, cate2: undefined })}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={5}>
            <Form.Item label="二级分类">
              <Select
                allowClear
                placeholder="全部"
                value={value.cate2 || undefined}
                disabled={!selectedCategory}
                options={selectedCategory?.children.map((item) => ({ value: item.id, label: item.name })) || []}
                onChange={(cate2) => update({ cate2 })}
              />
            </Form.Item>
          </Col>
          {mode === 'resources' ? (
            <Col xs={12} md={4}>
              <Form.Item label="资源类型">
                <Select
                  allowClear
                  placeholder="全部"
                  value={value.type || undefined}
                  options={resourceTypes.map((item) => ({ value: item, label: item }))}
                  onChange={(type) => update({ type })}
                />
              </Form.Item>
            </Col>
          ) : (
            <Col xs={12} md={4}>
              <Form.Item label="状态">
                <Select
                  allowClear
                  placeholder="全部"
                  value={value.status || undefined}
                  options={[
                    { value: 'active', label: '进行中' },
                    { value: 'solved', label: '已解决' },
                  ]}
                  onChange={(status) => update({ status })}
                />
              </Form.Item>
            </Col>
          )}
          <Col xs={12} md={3}>
            <Form.Item label="排序">
              <Select
                value={value.sort || 'latest'}
                options={
                  mode === 'resources'
                    ? [
                        { value: 'latest', label: '最新' },
                        { value: 'download', label: '下载' },
                        { value: 'score', label: '评分' },
                      ]
                    : [
                        { value: 'latest', label: '最新' },
                        { value: 'points', label: '悬赏' },
                        { value: 'reply', label: '回答' },
                      ]
                }
                onChange={(sort) => update({ sort })}
              />
            </Form.Item>
          </Col>
        </Row>
        <Button onClick={() => onChange({ page: 1, pageSize: value.pageSize || 10, sort: 'latest' })}>重置筛选</Button>
      </Form>
    </div>
  );
}
