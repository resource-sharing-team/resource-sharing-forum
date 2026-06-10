import { message } from 'antd';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useCategories, useDownloadAttachment, useRateResource, useResource, useResourceAction } from '../api/hooks';
import { ApiError } from '../components/ApiState';
import CommentPanel from '../components/CommentPanel';
import ReportModal from '../components/ReportModal';
import type { ResourceAttachment } from '../types';
import { formatCategory } from '../utils/format';

export default function ResourceDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resourceQuery = useResource(id);
  const categoriesQuery = useCategories();
  const action = useResourceAction();
  const download = useDownloadAttachment();
  const rateResource = useRateResource();
  const [reportOpen, setReportOpen] = useState(false);
  const [reportTarget, setReportTarget] = useState<'RESOURCE' | 'COPYRIGHT'>('RESOURCE');
  const [downloadError, setDownloadError] = useState('');

  if (resourceQuery.isLoading) {
    return <div className="container"><div className="card"><div className="card-body">加载中...</div></div></div>;
  }

  if (resourceQuery.error) {
    return <div className="container"><div className="card"><div className="card-body"><ApiError error={resourceQuery.error} /></div></div></div>;
  }

  if (!resourceQuery.data) {
    return (
      <div className="container">
        <div className="card">
          <div className="card-body">
            资源不存在，<Link to="/resources">返回资源库</Link>
          </div>
        </div>
      </div>
    );
  }

  const { resource, comments } = resourceQuery.data;
  const categories = categoriesQuery.data || [];
  const shownRating = resource.userRating || Math.round(resource.score);

  async function downloadAttachment(attachment: ResourceAttachment) {
    setDownloadError('');
    try {
      const file = await download.mutateAsync(attachment.id);
      saveBlob(file.blob, file.fileName || attachment.name);
      message.success(`正在下载：${file.fileName || attachment.name}`);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '接口调用失败';
      setDownloadError(errorMessage);
      message.error(errorMessage);
    }
  }

  function openReport(target: 'RESOURCE' | 'COPYRIGHT') {
    setReportTarget(target);
    setReportOpen(true);
  }

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
            {resource.title}
            <span className="copyright-tag" onClick={() => openReport('COPYRIGHT')}>
              版权投诉
            </span>
          </div>

          <div className="detail-meta">
            <span>分类：{formatCategory(resource.category1, resource.category2, categories)}</span>
            <span>类型：{resource.type}</span>
            <span>发布者：{resource.author}</span>
            <span>发布时间：{resource.date}</span>
          </div>
          <div className="detail-meta">
            <span>下载：{resource.downloads}</span>
            <span>附件：{resource.attachments.length}</span>
            <span>评分：{resource.score.toFixed(1)}</span>
            <span>评分人数：{resource.ratingCount}</span>
          </div>
          <div className="resource-tags">
            {resource.tags.map((tag) => (
              <span className="resource-tag round" key={tag}>
                {tag}
              </span>
            ))}
          </div>

          <div className="action-group">
            <button
              className={`action-item ${resource.liked ? 'active' : ''}`}
              onClick={async () => {
                try {
                  await action.mutateAsync({ id: resource.id, action: 'like' });
                  message.success('点赞状态已更新');
                } catch (error) {
                  message.error(error instanceof Error ? error.message : '接口调用失败');
                }
              }}
            >
              <span>👍</span>
              <span>{resource.liked ? '已点赞' : '点赞'}</span>
            </button>
            <button
              className={`action-item ${resource.favorited ? 'active' : ''}`}
              onClick={async () => {
                try {
                  await action.mutateAsync({ id: resource.id, action: 'favorite' });
                  message.success('收藏状态已更新');
                } catch (error) {
                  message.error(error instanceof Error ? error.message : '接口调用失败');
                }
              }}
            >
              <span>⭐</span>
              <span>{resource.favorited ? '已收藏' : '收藏'}</span>
            </button>
            <div className="action-item">
              <span>评分：</span>
              <div className="score-stars">
                {[1, 2, 3, 4, 5].map((score) => (
                  <button
                    className={`star ${score <= shownRating ? 'active' : ''}`}
                    key={score}
                    onClick={async () => {
                      try {
                        await rateResource.mutateAsync({ id: resource.id, score });
                        message.success(`已评分 ${score} 分`);
                      } catch (error) {
                        message.error(error instanceof Error ? error.message : '接口调用失败');
                      }
                    }}
                  >
                    ★
                  </button>
                ))}
              </div>
            </div>
            <button className="action-item" style={{ color: '#f44336' }} onClick={() => openReport('RESOURCE')}>
              <span>🚩</span>
              <span>举报</span>
            </button>
          </div>

          <div className="section-title">资源介绍</div>
          <div className="desc">{resource.detail || resource.description}</div>

          <div className="section-title">附件列表</div>
          {downloadError && <div className="api-error-inline">{downloadError}</div>}
          {resource.attachments.map((attachment) => (
            <div className="attach-item" key={attachment.id}>
              <div className="attach-info">
                <div className="attach-name">{attachment.name}</div>
                <div className="attach-size">{attachment.size}</div>
              </div>
              <button className="download-btn" onClick={() => downloadAttachment(attachment)} disabled={download.isPending || action.isPending}>
                下载此附件
              </button>
            </div>
          ))}
        </div>
      </div>

      <CommentPanel kind="resources" id={resource.id} comments={comments} ownerName={resource.author} />
      <ReportModal open={reportOpen} target={reportTarget} targetId={resource.id} subjectTitle={resource.title} onClose={() => setReportOpen(false)} />
    </div>
  );
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName || 'attachment';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
