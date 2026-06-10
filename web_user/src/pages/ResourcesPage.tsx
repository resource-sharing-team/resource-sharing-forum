import { PlusOutlined } from '@ant-design/icons';
import { Button, Empty, Pagination, Spin, message } from 'antd';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useResourceAction, useResources } from '../api/hooks';
import ListingFilter from '../components/ListingFilter';
import ResourceCard from '../components/ResourceCard';
import type { ListParams } from '../types';
import { listParamsToSearchParams, resourceListParamsFromSearch } from '../utils/listParams';

export default function ResourcesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const searchKey = searchParams.toString();
  const params = useMemo(() => resourceListParamsFromSearch(searchParams), [searchKey, searchParams]);
  const updateParams = (next: ListParams | ((prev: ListParams) => ListParams)) => {
    const resolved = typeof next === 'function' ? next(params) : next;
    setSearchParams(listParamsToSearchParams(resolved, 'resources'), { replace: true });
  };

  const resourcesQuery = useResources(params);
  const action = useResourceAction();

  const items = useMemo(() => resourcesQuery.data?.items || [], [resourcesQuery.data?.items]);

  return (
    <>
      <div className="resource-header">
        <h1>资源库</h1>
        <Button className="publish-btn" type="primary" icon={<PlusOutlined />} onClick={() => navigate('/publish-resource')}>
          发布资源
        </Button>
      </div>

      <ListingFilter mode="resources" value={params} onChange={updateParams} />

      <div className="card">
        <div className="card-body">
          <div className="sort-bar">
            <div>共 {resourcesQuery.data?.total || 0} 个公开资源（仅显示已发布）</div>
            <div className="sort-items">
              {[
                ['latest', '最新发布'],
                ['download', '下载最多'],
                ['score', '评分最高'],
              ].map(([key, label]) => (
                <button
                  type="button"
                  key={key}
                  className={params.sort === key ? 'active' : undefined}
                  onClick={() => updateParams((prev) => ({ ...prev, sort: key, page: 1 }))}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <Spin spinning={resourcesQuery.isLoading || action.isPending}>
          <div className="card-body" style={{ padding: 0 }}>
            {items.map((resource) => (
              <ResourceCard
                key={resource.id}
                resource={resource}
                onFavorite={async (id) => {
                  try {
                    await action.mutateAsync({ id, action: 'favorite' });
                    message.success('收藏状态已更新');
                  } catch (error) {
                    message.error(error instanceof Error ? error.message : '收藏失败，请稍后重试');
                  }
                }}
                onLike={async (id) => {
                  try {
                    await action.mutateAsync({ id, action: 'like' });
                    message.success('点赞状态已更新');
                  } catch (error) {
                    message.error(error instanceof Error ? error.message : '点赞失败，请稍后重试');
                  }
                }}
              />
            ))}
            {!items.length && !resourcesQuery.isLoading && <Empty description="暂无匹配资源" style={{ padding: '40px 0' }} />}
          </div>
        </Spin>
      </div>

      <Pagination
        className="page-pagination"
        current={params.page}
        pageSize={params.pageSize}
        total={resourcesQuery.data?.total || 0}
        onChange={(page, pageSize) => updateParams((prev) => ({ ...prev, page, pageSize }))}
      />
    </>
  );
}
