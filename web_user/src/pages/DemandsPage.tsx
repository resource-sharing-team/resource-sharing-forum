import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCategories, useDemands } from '../api/hooks';
import { InlineApiError } from '../components/ApiState';
import DemandCard from '../components/DemandCard';
import ListingFilter from '../components/ListingFilter';
import type { ListParams } from '../types';

export default function DemandsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useState<ListParams>({ page: 1, pageSize: 5, sort: 'latest', status: 'active' });
  const demandsQuery = useDemands(params);
  const categoriesQuery = useCategories();
  const categories = categoriesQuery.data || [];
  const items = (demandsQuery.data?.items || []).filter((demand) => demand.status === 'active' || demand.status === 'solved');
  const total = demandsQuery.data?.total || 0;
  const totalPages = Math.max(1, Math.ceil(total / (params.pageSize || 5)));

  const goPage = (page: number) => setParams((prev) => ({ ...prev, page }));

  return (
    <div className="container">
      <div className="page-header">
        <h2>求资源</h2>
        <button className="publish-btn" onClick={() => navigate('/publish-demand')}>
          + 发布求资源
        </button>
      </div>

      <ListingFilter mode="demands" value={params} categories={categories} categoriesError={categoriesQuery.error} onChange={setParams} />

      <div className="card">
        <div className="card-title">求资源列表</div>
        <div className="card-body">
          {items.map((demand) => (
            <DemandCard demand={demand} categories={categories} key={demand.id} />
          ))}
          {demandsQuery.isError && <InlineApiError error={demandsQuery.error} />}
          {!items.length && <div style={{ padding: 40, textAlign: 'center', color: '#999' }}>{demandsQuery.isLoading ? '加载中...' : '暂无求资源需求'}</div>}
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
