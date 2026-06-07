import { Link } from 'react-router-dom';
import type { Demand } from '../types';
import { formatCategory } from '../utils/format';

export default function DemandCard({ demand }: { demand: Demand; compact?: boolean }) {
  const solved = demand.status === 'solved';

  return (
    <div className="demand-card">
      <Link className="demand-title" to={`/demands/${demand.id}`}>
        {demand.title}
      </Link>
      <div className="demand-desc">{demand.description}</div>
      <div className="demand-meta">
        <span>{formatCategory(demand.category1, demand.category2)}</span>
        <span className="demand-points">悬赏：{demand.points ? `${demand.points}积分` : '0（免费）'}</span>
        <span>回复：{demand.replyCount}</span>
        <span>发布者：{demand.author}</span>
        <span>{demand.date}</span>
        <span className={`demand-status ${solved ? 'solved' : ''}`}>{solved ? '已解决' : '进行中'}</span>
        {demand.tags.map((tag) => (
          <span className="demand-tag" key={tag}>
            {tag}
          </span>
        ))}
      </div>
    </div>
  );
}
