import { PlusOutlined } from '@ant-design/icons';
import { Button, Empty, Pagination, Spin } from 'antd';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useDemands } from '../api/hooks';
import DemandCard from '../components/DemandCard';
import ListingFilter from '../components/ListingFilter';
import type { ListParams } from '../types';
import { demandListParamsFromSearch, listParamsToSearchParams } from '../utils/listParams';

export default function DemandsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const searchKey = searchParams.toString();
  const params = useMemo(() => demandListParamsFromSearch(searchParams), [searchKey, searchParams]);
  const updateParams = (next: ListParams | ((prev: ListParams) => ListParams)) => {
    const resolved = typeof next === 'function' ? next(params) : next;
    setSearchParams(listParamsToSearchParams(resolved, 'demands'), { replace: true });
  };

  const demandsQuery = useDemands(params);
  const items = demandsQuery.data?.items || [];

  return (
    <>
      <div className="page-header">
        <h1>求资源</h1>
        <Button className="publish-btn" type="primary" icon={<PlusOutlined />} onClick={() => navigate('/publish-demand')}>
          发布求资源
        </Button>
      </div>

      <ListingFilter mode="demands" value={params} onChange={updateParams} />

      <div className="card">
        <div className="card-body">
          <div className="sort-bar">
            <div>共 {demandsQuery.data?.total || 0} 条求资源</div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-title">求资源列表</div>
        <Spin spinning={demandsQuery.isLoading}>
          <div className="card-body" style={{ padding: 0 }}>
            {items.map((demand) => (
              <DemandCard key={demand.id} demand={demand} />
            ))}
            {!items.length && !demandsQuery.isLoading && <Empty description="暂无匹配求资源" style={{ padding: '40px 0' }} />}
          </div>
        </Spin>
      </div>

      <Pagination
        className="page-pagination"
        current={params.page}
        pageSize={params.pageSize}
        total={demandsQuery.data?.total || 0}
        onChange={(page, pageSize) => updateParams((prev) => ({ ...prev, page, pageSize }))}
      />
    </>
  );
}
