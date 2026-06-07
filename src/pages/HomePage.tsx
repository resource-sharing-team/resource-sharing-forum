import { useNavigate } from 'react-router-dom';
import { useAnnouncements, useCategories, useResources } from '../api/hooks';
import { InlineApiError } from '../components/ApiState';
import ResourceCard from '../components/ResourceCard';

export default function HomePage() {
  const navigate = useNavigate();
  const latestQuery = useResources({ page: 1, pageSize: 2, sort: 'latest' });
  const hotQuery = useResources({ page: 1, pageSize: 2, sort: 'download' });
  const categoriesQuery = useCategories();
  const announcementsQuery = useAnnouncements({ page: 1, pageSize: 5 });
  const latest = latestQuery.data?.items || [];
  const hot = hotQuery.data?.items || [];
  const recommended = (categoriesQuery.data || [])
    .flatMap((parent) => parent.children.map((child) => ({ ...child, parentId: parent.id })))
    .slice(0, 6);
  const notices = announcementsQuery.data?.items || [];

  return (
    <div className="container">
      <div className="wrap">
        <div className="main">
          <div className="card">
            <div className="card-title">最新资源</div>
            <div className="card-body">
              {latest.map((resource) => (
                <ResourceCard resource={resource} compact key={resource.id} />
              ))}
              {latestQuery.isError && <InlineApiError error={latestQuery.error} />}
              {!latest.length && <div className="tip" style={{ textAlign: 'center', padding: 20 }}>暂无资源</div>}
            </div>
          </div>

          <div className="card">
            <div className="card-title">热门资源</div>
            <div className="card-body">
              {hot.map((resource) => (
                <ResourceCard resource={resource} compact key={resource.id} />
              ))}
              {hotQuery.isError && <InlineApiError error={hotQuery.error} />}
              {!hot.length && <div className="tip" style={{ textAlign: 'center', padding: 20 }}>暂无资源</div>}
            </div>
          </div>
        </div>

        <aside className="sidebar">
          <div className="card">
            <div className="card-title">推荐分类</div>
            <div className="card-body">
              <div className="category-list">
                {categoriesQuery.isError && <InlineApiError error={categoriesQuery.error} />}
                {recommended.map((category) => (
                  <button className="category-item" key={category.id} onClick={() => navigate(`/resources?cate1=${category.parentId}&cate2=${category.id}`)}>
                    <div className="category-name">{category.name}</div>
                    <div className="category-desc">查看该分类资源</div>
                  </button>
                ))}
                {!recommended.length && !categoriesQuery.isError && <div className="tip">暂无推荐分类</div>}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-title">平台公告</div>
            <div className="card-body">
              <div className="notice-list">
                {announcementsQuery.isError && <InlineApiError error={announcementsQuery.error} />}
                {notices.map((notice) => (
                  <div className="notice-item" key={notice.id}>
                    {notice.title}
                  </div>
                ))}
                {!notices.length && !announcementsQuery.isError && <div className="tip">暂无公告</div>}
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
