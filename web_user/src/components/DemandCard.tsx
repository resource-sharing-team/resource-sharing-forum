import { Link } from 'react-router-dom';
import { getCategoryName } from '../data/catalog';
import type { Demand } from '../types';

export default function DemandCard({ demand, compact }: { demand: Demand; compact?: boolean }) {
  const solved = demand.status === 'solved';

  return (
    <article className="demand-card">
      <Link className="demand-title" to={`/demands/${demand.id}`}>
        {demand.title}
      </Link>
      <div className="demand-desc">{demand.description}</div>
      <div className="demand-meta">
        <span>{getCategoryName(demand.category1, demand.category2)}</span>
        <span className="demand-points">{demand.points > 0 ? `${demand.points} 积分` : '0（免费）'}</span>
        <span>回复：{demand.replyCount}</span>
        <span>发布者：{demand.author}</span>
        <span>{demand.date}</span>
        <span className={solved ? 'demand-status solved' : 'demand-status'}>{solved ? '已解决' : '进行中'}</span>
        {!compact && <span>期望：{demand.format}</span>}
      </div>
      <div className="demand-tags">
        {demand.tags.map((tag) => (
          <span className="demand-tag" key={tag}>
            {tag}
          </span>
        ))}
      </div>
      {!compact && (
        <div className="demand-actions">
          <Link className="text-btn" to={`/demands/${demand.id}`}>
            查看详情
          </Link>
        </div>
      )}
    </article>
  );
}
