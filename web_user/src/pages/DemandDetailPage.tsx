import { Result, Spin } from 'antd';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useDemand } from '../api/hooks';
import CommentPanel from '../components/CommentPanel';
import ReportModal from '../components/ReportModal';
import { getCategoryName } from '../data/catalog';

export default function DemandDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const demandQuery = useDemand(id);
  const [reportOpen, setReportOpen] = useState(false);

  if (demandQuery.isLoading) return <Spin fullscreen />;
  if (!demandQuery.data) return <Result status="404" title="求资源不存在" extra={<Link to="/demands">返回求资源</Link>} />;

  const { demand, comments } = demandQuery.data;

  return (
    <>
      <div className="back-btn-wrapper">
        <button type="button" className="back-btn" onClick={() => navigate(-1)}>
          ← 返回
        </button>
      </div>

      <section className="card detail-card">
        <div className="card-body">
          <h1 className="resource-detail-title">{demand.title}</h1>
          <div className="detail-meta">
            <span>分类：{getCategoryName(demand.category1, demand.category2)}</span>
            <span>期望格式：{demand.format}</span>
            <span>发布者：{demand.author}</span>
            <span>发布时间：{demand.date}</span>
          </div>
          <div className="detail-meta">
            <span className="demand-points">{demand.points > 0 ? `悬赏 ${demand.points} 积分` : '0（免费）'}</span>
            <span>回复：{demand.replyCount}</span>
            <span className={demand.status === 'solved' ? 'demand-status solved' : 'demand-status'}>{demand.status === 'solved' ? '已解决' : '进行中'}</span>
          </div>

          <div className="demand-tags">
            {demand.tags.map((tag) => (
              <span className="demand-tag" key={tag}>
                {tag}
              </span>
            ))}
          </div>

          <div className="action-group">
            <button type="button" className="text-btn danger" onClick={() => setReportOpen(true)}>
              举报
            </button>
          </div>

          <div className="detail-section-title">需求说明</div>
          <div className="desc">{demand.description}</div>
        </div>
      </section>

      <CommentPanel kind="demands" id={demand.id} comments={comments} />
      <ReportModal open={reportOpen} target="DEMAND" targetId={demand.id} onClose={() => setReportOpen(false)} />
    </>
  );
}
