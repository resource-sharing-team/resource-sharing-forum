import { DownloadOutlined, HeartFilled, HeartOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { Button, Card, Rate, Space, Statistic, Tag, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { getCategoryName } from '../data/catalog';
import type { Resource } from '../types';

type Props = {
  resource: Resource;
  compact?: boolean;
  onFavorite?: (id: number) => void;
  onLike?: (id: number) => void;
};

export default function ResourceCard({ resource, compact, onFavorite, onLike }: Props) {
  return (
    <Card
      className="resource-card"
      title={<Link className="card-title-link" to={`/resources/${resource.id}`}>{resource.title}</Link>}
      extra={<Tag color="green">{resource.type}</Tag>}
      actions={
        compact
          ? undefined
          : [
              <Button
                key="favorite"
                type="text"
                className={resource.favorited ? 'action-active favorite' : undefined}
                icon={resource.favorited ? <StarFilled /> : <StarOutlined />}
                onClick={() => onFavorite?.(resource.id)}
              >
                {resource.favorited ? '已收藏' : '收藏'}
              </Button>,
              <Button
                key="like"
                type="text"
                className={resource.liked ? 'action-active like' : undefined}
                icon={resource.liked ? <HeartFilled /> : <HeartOutlined />}
                onClick={() => onLike?.(resource.id)}
              >
                {resource.liked ? '已点赞' : '点赞'}
              </Button>,
              <Link key="detail" to={`/resources/${resource.id}`}>查看详情</Link>,
            ]
      }
    >
      <Typography.Paragraph className="card-desc" ellipsis={{ rows: compact ? 2 : 3 }}>
        {resource.description}
      </Typography.Paragraph>
      <div className="tag-row">
        {resource.tags.map((tag) => (
          <Tag key={tag}>{tag}</Tag>
        ))}
      </div>
      <Space wrap size="middle" style={{ marginTop: 14 }}>
        <Typography.Text type="secondary">{getCategoryName(resource.category1, resource.category2)}</Typography.Text>
        <Typography.Text type="secondary">发布者：{resource.author}</Typography.Text>
        <Typography.Text type="secondary">{resource.date}</Typography.Text>
      </Space>
      {!compact && (
        <Space size={28} style={{ width: '100%', marginTop: 16 }} wrap>
          <Statistic prefix={<DownloadOutlined />} value={resource.downloads} suffix="次" />
          <Space direction="vertical" size={2}>
            <Rate allowHalf disabled value={resource.score} />
            <Typography.Text type="secondary">{resource.score.toFixed(1)} 分 / {resource.ratingCount} 人</Typography.Text>
          </Space>
          <Typography.Text type="secondary">{resource.attachments.length} 个附件</Typography.Text>
        </Space>
      )}
    </Card>
  );
}
