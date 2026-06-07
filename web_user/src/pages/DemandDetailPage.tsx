import { ExclamationCircleOutlined, TrophyOutlined } from '@ant-design/icons';
import { Button, Descriptions, Result, Space, Spin, Tag, Typography } from 'antd';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useDemand } from '../api/hooks';
import CommentPanel from '../components/CommentPanel';
import ReportModal from '../components/ReportModal';
import { getCategoryName } from '../data/catalog';

export default function DemandDetailPage() {
  const { id } = useParams();
  const demandQuery = useDemand(id);
  const [reportOpen, setReportOpen] = useState(false);

  if (demandQuery.isLoading) return <Spin fullscreen />;
  if (!demandQuery.data) return <Result status="404" title="求资源不存在" extra={<Link to="/demands">返回求资源</Link>} />;

  const { demand, comments } = demandQuery.data;

  return (
    <>
      <section className="detail-hero">
        <Space wrap>
          <Tag color={demand.status === 'solved' ? 'blue' : 'orange'}>
            {demand.status === 'solved' ? '已解决' : '进行中'}
          </Tag>
          <Tag>{getCategoryName(demand.category1, demand.category2)}</Tag>
          <Typography.Text type="secondary">发布于 {demand.date}</Typography.Text>
        </Space>
        <h1 className="detail-title">{demand.title}</h1>
        <Typography.Paragraph style={{ fontSize: 16, lineHeight: 1.8 }}>{demand.description}</Typography.Paragraph>
        <div className="tag-row">
          {demand.tags.map((tag) => (
            <Tag key={tag}>{tag}</Tag>
          ))}
        </div>
        <div className="detail-actions">
          <Button type="primary" icon={<TrophyOutlined />}>悬赏 {demand.points} 积分</Button>
          <Button danger icon={<ExclamationCircleOutlined />} onClick={() => setReportOpen(true)}>
            举报
          </Button>
        </div>
      </section>

      <Descriptions bordered column={{ xs: 1, md: 2 }} style={{ marginTop: 18 }}>
        <Descriptions.Item label="发布者">{demand.author}</Descriptions.Item>
        <Descriptions.Item label="期望格式">{demand.format}</Descriptions.Item>
        <Descriptions.Item label="回答数">{demand.replyCount}</Descriptions.Item>
        <Descriptions.Item label="状态">{demand.status === 'solved' ? '已解决' : '进行中'}</Descriptions.Item>
      </Descriptions>

      <CommentPanel kind="demands" id={demand.id} comments={comments} />
      <ReportModal open={reportOpen} target="DEMAND" targetId={demand.id} onClose={() => setReportOpen(false)} />
    </>
  );
}
