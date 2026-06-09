import { HeartFilled, HeartOutlined, StarFilled, StarOutlined } from '@ant-design/icons';
import { Rate } from 'antd';
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
    <article className="resource-card">
      <Link className="resource-title" to={`/resources/${resource.id}`}>
        {resource.title}
      </Link>
      <div className="resource-desc">{resource.description}</div>
      <div className="resource-meta">
        <span>{getCategoryName(resource.category1, resource.category2)}</span>
        <span>类型：{resource.type}</span>
        <span>发布者：{resource.author}</span>
        <span>下载：{resource.downloads}</span>
        <span>评分：{resource.score.toFixed(1)}</span>
        <span>{resource.date}</span>
      </div>
      <div className="resource-tags">
        {resource.tags.map((tag) => (
          <span className="resource-tag" key={tag}>
            {tag}
          </span>
        ))}
      </div>
      {!compact && (
        <div className="resource-actions">
          <button
            type="button"
            className={resource.favorited ? 'text-btn active' : 'text-btn'}
            onClick={() => onFavorite?.(resource.id)}
          >
            {resource.favorited ? <StarFilled /> : <StarOutlined />} {resource.favorited ? '已收藏' : '收藏'}
          </button>
          <button type="button" className={resource.liked ? 'text-btn active' : 'text-btn'} onClick={() => onLike?.(resource.id)}>
            {resource.liked ? <HeartFilled /> : <HeartOutlined />} {resource.liked ? '已点赞' : '点赞'}
          </button>
          <span className="text-btn">
            <Rate allowHalf disabled value={resource.score} style={{ fontSize: 13 }} /> {resource.ratingCount} 人
          </span>
          <span className="text-btn">{resource.attachments.length} 个附件</span>
          <Link className="text-btn" to={`/resources/${resource.id}`}>
            查看详情
          </Link>
        </div>
      )}
    </article>
  );
}
