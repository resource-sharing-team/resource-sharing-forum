import { ArrowRightOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Col, Empty, Input, Row, Space, Spin, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDemands, useResources } from '../api/hooks';
import DemandCard from '../components/DemandCard';
import ResourceCard from '../components/ResourceCard';
import { categories } from '../data/catalog';

export default function HomePage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const resourcesQuery = useResources({ page: 1, pageSize: 3, sort: 'latest' });
  const demandsQuery = useDemands({ page: 1, pageSize: 3, sort: 'latest' });

  const search = () => {
    navigate(`/resources?keyword=${encodeURIComponent(keyword)}`);
  };

  return (
    <>
      <section className="hero-panel">
        <div className="hero-content">
          <p className="section-kicker">RESOURCE EXCHANGE</p>
          <h1 className="hero-title">把散落的资料，变成可检索、可反馈、可沉淀的共享库。</h1>
          <p className="hero-copy">
            用户端已经接入列表、详情、评论、发布、收藏、点赞和个人中心流程，可通过环境配置连接真实后端或本地 mock API。
          </p>
          <div className="hero-tools">
            <Input.Search
              size="large"
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索资源标题、标签、发布者"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onSearch={search}
              style={{ maxWidth: 470 }}
            />
            <Button size="large" type="primary" icon={<UploadOutlined />} onClick={() => navigate('/publish-resource')}>
              发布资源
            </Button>
            <Button size="large" onClick={() => navigate('/demands')}>
              看求资源
            </Button>
          </div>
        </div>
        <div className="hero-ledger">
          <div className="ledger-item">
            <span className="ledger-value">{resourcesQuery.data?.total || 0}</span>
            <span className="ledger-label">可用资源</span>
          </div>
          <div className="ledger-item">
            <span className="ledger-value">{demandsQuery.data?.total || 0}</span>
            <span className="ledger-label">求资源帖子</span>
          </div>
          <div className="ledger-item">
            <span className="ledger-value">/api</span>
            <span className="ledger-label">统一接口前缀</span>
          </div>
        </div>
      </section>

      <section>
        <div className="section-head">
          <div>
            <p className="section-kicker">LATEST RESOURCES</p>
            <h2 className="section-title">最新资源</h2>
          </div>
          <Button icon={<ArrowRightOutlined />} onClick={() => navigate('/resources')}>
            全部资源
          </Button>
        </div>
        <Spin spinning={resourcesQuery.isLoading}>
          <Row gutter={[16, 16]}>
            {resourcesQuery.data?.items.map((resource) => (
              <Col xs={24} lg={8} key={resource.id}>
                <ResourceCard resource={resource} compact />
              </Col>
            ))}
          </Row>
        </Spin>
      </section>

      <section style={{ marginTop: 28 }}>
        <div className="section-head">
          <div>
            <p className="section-kicker">DEMAND BOARD</p>
            <h2 className="section-title">正在寻找</h2>
          </div>
          <Button icon={<ArrowRightOutlined />} onClick={() => navigate('/demands')}>
            全部求资源
          </Button>
        </div>
        <Spin spinning={demandsQuery.isLoading}>
          <Row gutter={[16, 16]}>
            {demandsQuery.data?.items.map((demand) => (
              <Col xs={24} lg={8} key={demand.id}>
                <DemandCard demand={demand} compact />
              </Col>
            ))}
          </Row>
        </Spin>
      </section>

      <section style={{ marginTop: 28 }}>
        <div className="section-head">
          <div>
            <p className="section-kicker">CATALOG</p>
            <h2 className="section-title">资源分类</h2>
          </div>
        </div>
        <Row gutter={[12, 12]}>
          {categories.length ? (
            categories.map((category) => (
              <Col xs={24} sm={12} lg={8} key={category.id}>
                <button
                  type="button"
                  className="ledger-item"
                  style={{ width: '100%', textAlign: 'left', cursor: 'pointer' }}
                  onClick={() => navigate(`/resources?cate1=${category.id}`)}
                >
                  <Typography.Title level={4} style={{ margin: 0 }}>{category.name}</Typography.Title>
                  <Space wrap className="muted" style={{ marginTop: 8 }}>
                    {category.children.map((child) => child.name).join(' / ')}
                  </Space>
                </button>
              </Col>
            ))
          ) : (
            <Empty />
          )}
        </Row>
      </section>
    </>
  );
}
