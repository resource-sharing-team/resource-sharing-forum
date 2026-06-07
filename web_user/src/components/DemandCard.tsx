import { CheckCircleOutlined, MessageOutlined, TrophyOutlined } from '@ant-design/icons';
import { Card, Space, Tag, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { getCategoryName } from '../data/catalog';
import type { Demand } from '../types';

export default function DemandCard({ demand, compact }: { demand: Demand; compact?: boolean }) {
  const solved = demand.status === 'solved';

  return (
    <Card
      className="demand-card"
      title={<Link className="card-title-link" to={`/demands/${demand.id}`}>{demand.title}</Link>}
      extra={<Tag color={solved ? 'blue' : 'orange'} icon={solved ? <CheckCircleOutlined /> : <TrophyOutlined />}>{solved ? '已解决' : `${demand.points} 积分`}</Tag>}
    >
      <Typography.Paragraph className="card-desc" ellipsis={{ rows: compact ? 2 : 3 }}>
        {demand.description}
      </Typography.Paragraph>
      <div className="tag-row">
        {demand.tags.map((tag) => (
          <Tag key={tag}>{tag}</Tag>
        ))}
      </div>
      <Space wrap size="middle" style={{ marginTop: 14 }}>
        <Typography.Text type="secondary">{getCategoryName(demand.category1, demand.category2)}</Typography.Text>
        <Typography.Text type="secondary">期望：{demand.format}</Typography.Text>
        <Typography.Text type="secondary" aria-label="回答数">
          <MessageOutlined /> {demand.replyCount}
        </Typography.Text>
        <Typography.Text type="secondary">{demand.date}</Typography.Text>
      </Space>
    </Card>
  );
}
