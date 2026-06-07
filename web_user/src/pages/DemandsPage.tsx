import { PlusOutlined } from '@ant-design/icons';
import { Button, Col, Empty, Pagination, Row, Spin } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDemands } from '../api/hooks';
import DemandCard from '../components/DemandCard';
import ListingFilter from '../components/ListingFilter';
import type { ListParams } from '../types';

export default function DemandsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useState<ListParams>({ page: 1, pageSize: 6, sort: 'latest' });
  const demandsQuery = useDemands(params);
  const items = demandsQuery.data?.items || [];

  return (
    <>
      <div className="section-head">
        <div>
          <p className="section-kicker">REQUEST BOARD</p>
          <h1 className="section-title">求资源列表</h1>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/publish-demand')}>
          发布求资源
        </Button>
      </div>

      <ListingFilter mode="demands" value={params} onChange={setParams} />

      <Spin spinning={demandsQuery.isLoading}>
        <Row gutter={[16, 16]}>
          {items.map((demand) => (
            <Col xs={24} md={12} key={demand.id}>
              <DemandCard demand={demand} />
            </Col>
          ))}
        </Row>
        {!items.length && !demandsQuery.isLoading && <Empty description="暂无匹配求资源" style={{ marginTop: 40 }} />}
      </Spin>

      <Pagination
        style={{ marginTop: 22, textAlign: 'right' }}
        current={params.page}
        pageSize={params.pageSize}
        total={demandsQuery.data?.total || 0}
        onChange={(page, pageSize) => setParams((prev) => ({ ...prev, page, pageSize }))}
      />
    </>
  );
}
