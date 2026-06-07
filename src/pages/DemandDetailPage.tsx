import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useDemand } from '../api/hooks';
import { ApiError } from '../components/ApiState';
import CommentPanel from '../components/CommentPanel';
import ReportModal from '../components/ReportModal';
import { formatCategory } from '../utils/format';

export default function DemandDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const demandQuery = useDemand(id);
  const [reportOpen, setReportOpen] = useState(false);

  if (demandQuery.isLoading) {
    return <div className="container"><div className="card"><div className="card-body">加载中...</div></div></div>;
  }

  if (demandQuery.error) {
    return <div className="container"><div className="card"><div className="card-body"><ApiError error={demandQuery.error} /></div></div></div>;
  }

  if (!demandQuery.data) {
    return (
      <div className="container">
        <div className="card">
          <div className="card-body">
            求资源不存在，<Link to="/demands">返回求资源</Link>
          </div>
        </div>
      </div>
    );
  }

  const { demand, comments } = demandQuery.data;

  return (
    <div className="container detail-container">
      <div className="back-btn-wrapper">
        <button className="back-btn" onClick={() => navigate(-1)}>
          <i>←</i> 返回
        </button>
      </div>

      <div className="card">
        <div className="card-body">
          <div className="resource-title resource-detail-title">
            {demand.title}
            <span className="copyright-tag" onClick={() => setReportOpen(true)}>
              版权投诉
            </span>
          </div>

          <div className="detail-meta">
            <span>分类：{formatCategory(demand.category1, demand.category2)}</span>
            <span>类型：求资源</span>
            <span>发布者：{demand.author}</span>
            <span>发布时间：{demand.date}</span>
            <span>悬赏积分：{demand.points}</span>
            <span>状态：{demand.status === 'solved' ? '已解决' : '进行中'}</span>
          </div>

          <div className="resource-tags">
            {demand.tags.map((tag) => (
              <span className="resource-tag round" key={tag}>
                {tag}
              </span>
            ))}
          </div>

          <div className="action-group">
            <button className="action-item" style={{ color: '#f44336' }} onClick={() => setReportOpen(true)}>
              <span>🚩</span>
              <span>举报</span>
            </button>
          </div>

          <div className="section-title">需求说明</div>
          <div className="desc">{demand.description}</div>

          <div className="section-title">期望格式</div>
          <div className="desc">{demand.format}</div>
        </div>
      </div>

      <CommentPanel kind="demands" id={demand.id} comments={comments} title="我来回答" ownerName={demand.author} />
      <ReportModal open={reportOpen} target="DEMAND" targetId={demand.id} onClose={() => setReportOpen(false)} />
    </div>
  );
}
