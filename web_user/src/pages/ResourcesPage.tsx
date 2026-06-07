import { PlusOutlined } from '@ant-design/icons';
import { Button, Col, Empty, Pagination, Row, Spin, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useResourceAction, useResources } from '../api/hooks';
import ListingFilter from '../components/ListingFilter';
import ResourceCard from '../components/ResourceCard';
import type { ListParams } from '../types';

export default function ResourcesPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [params, setParams] = useState<ListParams>({
    keyword: searchParams.get('keyword') || undefined,
    cate1: searchParams.get('cate1') || undefined,
    page: 1,
    pageSize: 6,
    sort: 'latest',
  });
  const resourcesQuery = useResources(params);
  const action = useResourceAction();

  const items = useMemo(() => resourcesQuery.data?.items || [], [resourcesQuery.data?.items]);

  return (
    <>
      <div className="section-head">
        <div>
          <p className="section-kicker">RESOURCE LIBRARY</p>
          <h1 className="section-title">资源库</h1>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/publish-resource')}>
          发布资源
        </Button>
      </div>

      <ListingFilter mode="resources" value={params} onChange={setParams} />

      <Spin spinning={resourcesQuery.isLoading || action.isPending}>
        <Row gutter={[16, 16]}>
          {items.map((resource) => (
            <Col xs={24} md={12} key={resource.id}>
              <ResourceCard
                resource={resource}
                onFavorite={async (id) => {
                  await action.mutateAsync({ id, action: 'favorite' });
                  message.success('收藏状态已更新');
                }}
                onLike={async (id) => {
                  await action.mutateAsync({ id, action: 'like' });
                  message.success('点赞状态已更新');
                }}
              />
            </Col>
          ))}
        </Row>
        {!items.length && !resourcesQuery.isLoading && <Empty description="暂无匹配资源" style={{ marginTop: 40 }} />}
      </Spin>

      <Pagination
        style={{ marginTop: 22, textAlign: 'right' }}
        current={params.page}
        pageSize={params.pageSize}
        total={resourcesQuery.data?.total || 0}
        onChange={(page, pageSize) => setParams((prev) => ({ ...prev, page, pageSize }))}
      />
    </>
  );
}
