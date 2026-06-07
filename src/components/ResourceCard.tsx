import { useNavigate } from 'react-router-dom';
import type { Resource } from '../types';
import { formatCategory } from '../utils/format';

type Props = {
  resource: Resource;
  compact?: boolean;
};

export default function ResourceCard({ resource }: Props) {
  const navigate = useNavigate();

  function openDetail() {
    navigate(`/resources/${resource.id}`);
  }

  return (
    <div
      className="resource-card resource-card-clickable"
      role="link"
      tabIndex={0}
      onClick={openDetail}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') openDetail();
      }}
    >
      <div className="resource-title">{resource.title}</div>
      <div className="resource-desc">{resource.description}</div>
      <div className="resource-meta">
        <span>{formatCategory(resource.category1, resource.category2)}</span>
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
    </div>
  );
}
