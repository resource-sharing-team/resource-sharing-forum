import {
  HeartFilled,
  HeartOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';
import { Button, Rate, Result, Spin, message } from 'antd';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useRateResource, useResource, useResourceAction } from '../api/hooks';
import CommentPanel from '../components/CommentPanel';
import ReportModal from '../components/ReportModal';
import { getCategoryName } from '../data/catalog';
import type { ResourceAttachment } from '../types';

export default function ResourceDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resourceQuery = useResource(id);
  const action = useResourceAction();
  const rateResource = useRateResource();
  const [reportOpen, setReportOpen] = useState(false);

  if (resourceQuery.isLoading) return <Spin fullscreen />;
  if (!resourceQuery.data) return <Result status="404" title="资源不存在" extra={<Link to="/resources">返回资源库</Link>} />;

  const { resource, comments } = resourceQuery.data;

  async function downloadAttachment(attachment: ResourceAttachment) {
    await action.mutateAsync({ id: resource.id, action: 'download', attachmentId: attachment.id });
    message.success(`已选择下载：${attachment.name}`);
  }

  return (
    <>
      <div className="back-btn-wrapper">
        <button type="button" className="back-btn" onClick={() => navigate(-1)}>
          ← 返回
        </button>
      </div>

      <section className="card detail-card">
        <div className="card-body">
          <h1 className="resource-detail-title">
            {resource.title}
            <button type="button" className="text-btn danger" style={{ marginLeft: 8 }} onClick={() => setReportOpen(true)}>
              版权投诉
            </button>
          </h1>

          <div className="detail-meta">
            <span>分类：{getCategoryName(resource.category1, resource.category2)}</span>
            <span>类型：{resource.type}</span>
            <span>发布者：{resource.author}</span>
            <span>发布时间：{resource.date}</span>
          </div>
          <div className="detail-meta">
            <span>下载：{resource.downloads}</span>
            <span>评分：{resource.score.toFixed(1)}</span>
            <span>评分人数：{resource.ratingCount}</span>
            <span>附件：{resource.attachments.length}</span>
          </div>

          <div className="resource-tags">
            {resource.tags.map((tag) => (
              <span className="resource-tag" key={tag}>
                {tag}
              </span>
            ))}
          </div>

          <div className="action-group">
            <button type="button" className={resource.liked ? 'text-btn active' : 'text-btn'} onClick={() => action.mutate({ id: resource.id, action: 'like' })}>
              {resource.liked ? <HeartFilled /> : <HeartOutlined />} {resource.liked ? '已点赞' : '点赞'}
            </button>
            <button type="button" className={resource.favorited ? 'text-btn active' : 'text-btn'} onClick={() => action.mutate({ id: resource.id, action: 'favorite' })}>
              {resource.favorited ? <StarFilled /> : <StarOutlined />} {resource.favorited ? '已收藏' : '收藏'}
            </button>
            <span className="action-item">
              <span>评分：</span>
              <Rate
                allowClear
                allowHalf
                value={resource.userRating || 0}
                onChange={async (score) => {
                  await rateResource.mutateAsync({ id: resource.id, score });
                  message.success(score ? `已评分 ${score} 分` : '已清除评分');
                }}
              />
            </span>
            <button type="button" className="text-btn danger" onClick={() => setReportOpen(true)}>
              举报
            </button>
          </div>

          <div className="detail-section-title">资源介绍</div>
          <div className="desc">{resource.detail || resource.description}</div>

          <div className="detail-section-title">附件列表</div>
          {resource.attachments.map((attachment) => (
            <div className="attach-item" key={attachment.id}>
              <div className="attach-info">
                <div className="attach-name">{attachment.name}</div>
                <div className="attach-size">
                  {attachment.type} / {attachment.size} / 已下载 {attachment.downloads} 次
                </div>
              </div>
              <button type="button" className="download-btn" disabled={action.isPending} onClick={() => downloadAttachment(attachment)}>
                下载此附件
              </button>
            </div>
          ))}
        </div>
      </section>

      <CommentPanel kind="resources" id={resource.id} comments={comments} />
      <ReportModal open={reportOpen} target="RESOURCE" targetId={resource.id} onClose={() => setReportOpen(false)} />
    </>
  );
}
