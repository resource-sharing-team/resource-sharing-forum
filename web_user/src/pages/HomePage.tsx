import { ArrowRightOutlined } from '@ant-design/icons';
import { Button, Spin } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useResources } from '../api/hooks';
import ResourceCard from '../components/ResourceCard';
import { categories } from '../data/catalog';

export default function HomePage() {
  const navigate = useNavigate();
  const latestQuery = useResources({ page: 1, pageSize: 2, sort: 'latest' });
  const hotQuery = useResources({ page: 1, pageSize: 2, sort: 'download' });

  return (
    <div className="home-wrap">
      <div className="main">
        <section className="card">
          <div className="card-title">最新资源</div>
          <Spin spinning={latestQuery.isLoading}>
            <div className="card-body" style={{ padding: 0 }}>
              {latestQuery.data?.items.map((resource) => <ResourceCard key={resource.id} resource={resource} compact />)}
            </div>
          </Spin>
        </section>

        <section className="card">
          <div className="card-title">热门资源</div>
          <Spin spinning={hotQuery.isLoading}>
            <div className="card-body" style={{ padding: 0 }}>
              {hotQuery.data?.items.map((resource) => <ResourceCard key={resource.id} resource={resource} compact />)}
            </div>
          </Spin>
        </section>

        <div className="section-head">
          <Button icon={<ArrowRightOutlined />} onClick={() => navigate('/resources')}>
            查看全部资源
          </Button>
          <Button className="publish-btn" type="primary" onClick={() => navigate('/publish-resource')}>
            发布资源
          </Button>
        </div>
      </div>

      <aside className="sidebar">
        <section className="card">
          <div className="card-title">推荐分类</div>
          <div className="card-body">
            <div className="category-list">
              {categories.slice(0, 6).map((category) => (
                <div className="category-item" key={category.id}>
                  <button type="button" className="category-main" onClick={() => navigate(`/resources?cate1=${category.id}`)}>
                    <span className="category-name">{category.name}</span>
                    <span className="category-desc">{category.children.slice(0, 3).map((child) => child.name).join('、')}</span>
                  </button>
                  <div className="category-child-row">
                    {category.children.slice(0, 3).map((child) => (
                      <button type="button" key={child.id} onClick={() => navigate(`/resources?cate1=${category.id}&cate2=${child.id}`)}>
                        {child.name}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="card">
          <div className="card-title">平台公告</div>
          <div className="card-body">
            <div className="notice-list">
              <div className="notice-item">【公告】资源审核标准与违规处理规则</div>
              <div className="notice-item">【通知】资源下载、评分与举报流程已接入真实后端</div>
              <div className="notice-item">【公告】积分与会员等级体系说明</div>
            </div>
          </div>
        </section>
      </aside>
    </div>
  );
}
