import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useCategories, useResourceTypes, useResources } from '../api/hooks';
import { InlineApiError } from '../components/ApiState';
import ListingFilter from '../components/ListingFilter';
import ResourceCard from '../components/ResourceCard';
import type { ListParams } from '../types';

const sorts = [
  { key: 'latest', label: '最新发布' },
  { key: 'download', label: '下载最多' },
  { key: 'score', label: '评分最高' },
];

export default function ResourcesPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [params, setParams] = useState<ListParams>({
    keyword: searchParams.get('keyword') || undefined,
    cate1: searchParams.get('cate1') || undefined,
    cate2: searchParams.get('cate2') || undefined,
    page: 1,
    pageSize: 10,
    sort: 'latest',
  });
  const resourcesQuery = useResources(params);
  const categoriesQuery = useCategories();
  const resourceTypesQuery = useResourceTypes();
  const items = useMemo(() => resourcesQuery.data?.items || [], [resourcesQuery.data?.items]);
  const total = resourcesQuery.data?.total || 0;
  const totalPages = Math.max(1, Math.ceil(total / (params.pageSize || 10)));

  const goPage = (page: number) => setParams((prev) => ({ ...prev, page }));

  return (
    <div className="container">
      <div className="resource-header">
        <h2>资源库</h2>
        <button className="publish-btn" onClick={() => navigate('/publish-resource')}>
          + 发布资源
        </button>
      </div>

      <ListingFilter
        mode="resources"
        value={params}
        categories={categoriesQuery.data || []}
        categoriesError={categoriesQuery.error}
        resourceTypes={resourceTypesQuery.data || []}
        resourceTypesError={resourceTypesQuery.error}
        onChange={setParams}
      />

      <div className="card">
        <div className="card-body">
          <div className="sort-bar">
            <div>共 {total} 个公开资源（仅显示已发布）</div>
            <div className="sort-items">
              {sorts.map((sort) => (
                <span
                  className={params.sort === sort.key ? 'active' : undefined}
                  key={sort.key}
                  onClick={() => setParams((prev) => ({ ...prev, page: 1, sort: sort.key }))}
                >
                  {sort.label}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-title">资源列表</div>
        <div className="card-body">
          {items.map((resource) => (
            <ResourceCard resource={resource} key={resource.id} />
          ))}
          {resourcesQuery.isError && <InlineApiError error={resourcesQuery.error} />}
          {!items.length && <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>{resourcesQuery.isLoading ? '加载中...' : '暂无资源'}</div>}
        </div>
      </div>

      {totalPages > 1 && (
        <div className="page-bar">
          {(params.page || 1) > 1 && (
            <button className="page-item" onClick={() => goPage((params.page || 1) - 1)}>
              上一页
            </button>
          )}
          {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
            <button className={`page-item ${page === (params.page || 1) ? 'active' : ''}`} key={page} onClick={() => goPage(page)}>
              {page}
            </button>
          ))}
          {(params.page || 1) < totalPages && (
            <button className="page-item" onClick={() => goPage((params.page || 1) + 1)}>
              下一页
            </button>
          )}
        </div>
      )}
    </div>
  );
}
