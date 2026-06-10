import { Link } from 'react-router-dom';
import type { Category, Demand } from '../types';
import { demandStatusLabel, formatCategory } from '../utils/format';

export default function DemandCard({ demand, categories = [] }: { demand: Demand; compact?: boolean; categories?: Category[] }) {
  const statusClass = demand.status === 'solved' ? 'solved' : demand.status === 'active' ? '' : 'inactive';

  return (
    <div className="demand-card">
      <Link className="demand-title" to={`/demands/${demand.id}`}>
        {demand.title}
      </Link>
      <div className="demand-desc">{demand.description}</div>
      <div className="demand-meta">
        <span>{formatCategory(demand.category1, demand.category2, categories)}</span>
        <span className="demand-points">悬赏：{demand.points ? `${demand.points}积分` : '0（免费）'}</span>
        <span>回复：{demand.replyCount}</span>
        <span>发布者：{demand.author}</span>
        <span>{demand.date}</span>
        <span className={`demand-status ${statusClass}`}>{demandStatusLabel(demand.status)}</span>
        {demand.tags.map((tag) => (
          <span className="demand-tag" key={tag}>
            {tag}
          </span>
        ))}
      </div>
    </div>
  );
}
