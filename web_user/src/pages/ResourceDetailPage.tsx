import {
  DownloadOutlined,
  ExclamationCircleOutlined,
  FileOutlined,
  HeartFilled,
  HeartOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';
import { Button, Descriptions, List, Rate, Result, Space, Spin, Tag, Typography, message } from 'antd';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useRateResource, useResource, useResourceAction } from '../api/hooks';
import CommentPanel from '../components/CommentPanel';
import ReportModal from '../components/ReportModal';
import { getCategoryName } from '../data/catalog';
import type { ResourceAttachment } from '../types';

export default function ResourceDetailPage() {
  const { id } = useParams();
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
      <section className="detail-hero">
        <Space wrap>
          <Tag color="green">{resource.type}</Tag>
          <Tag>{getCategoryName(resource.category1, resource.category2)}</Tag>
          <Typography.Text type="secondary">发布于 {resource.date}</Typography.Text>
        </Space>
        <h1 className="detail-title">{resource.title}</h1>
        <Typography.Paragraph style={{ fontSize: 16, lineHeight: 1.8 }}>{resource.description}</Typography.Paragraph>
        <div className="tag-row">
          {resource.tags.map((tag) => (
            <Tag key={tag}>{tag}</Tag>
          ))}
        </div>
        <div className="detail-rating">
          <Space align="center" wrap>
            <Typography.Text strong>资源评分</Typography.Text>
            <Rate
              allowClear
              allowHalf
              value={resource.userRating || 0}
              onChange={async (score) => {
                await rateResource.mutateAsync({ id: resource.id, score });
                message.success(score ? `已评分 ${score} 分` : '已清除评分');
              }}
            />
            <Typography.Text type="secondary">
              当前均分 {resource.score.toFixed(1)} / 5，{resource.ratingCount} 人评分
            </Typography.Text>
          </Space>
        </div>
        <div className="detail-actions">
          <Button
            icon={resource.favorited ? <StarFilled /> : <StarOutlined />}
            className={resource.favorited ? 'action-active favorite' : undefined}
            onClick={() => action.mutate({ id: resource.id, action: 'favorite' })}
          >
            {resource.favorited ? '已收藏' : '收藏'}
          </Button>
          <Button
            icon={resource.liked ? <HeartFilled /> : <HeartOutlined />}
            className={resource.liked ? 'action-active like' : undefined}
            onClick={() => action.mutate({ id: resource.id, action: 'like' })}
          >
            {resource.liked ? '已点赞' : '点赞'}
          </Button>
          <Button danger icon={<ExclamationCircleOutlined />} onClick={() => setReportOpen(true)}>
            举报/版权投诉
          </Button>
        </div>
      </section>

      <Descriptions bordered column={{ xs: 1, md: 2 }} style={{ marginTop: 18 }}>
        <Descriptions.Item label="发布者">{resource.author}</Descriptions.Item>
        <Descriptions.Item label="资源类型">{resource.type}</Descriptions.Item>
        <Descriptions.Item label="总下载次数">{resource.downloads}</Descriptions.Item>
        <Descriptions.Item label="附件数量">{resource.attachments.length}</Descriptions.Item>
        <Descriptions.Item label="详细说明" span={2}>{resource.detail}</Descriptions.Item>
      </Descriptions>

      <section className="detail-hero attachment-panel">
        <div className="section-head">
          <div>
            <p className="section-kicker">FILES</p>
            <h2 className="section-title">选择附件下载</h2>
          </div>
        </div>
        <List
          dataSource={resource.attachments}
          renderItem={(attachment) => (
            <List.Item
              actions={[
                <Button
                  key="download"
                  type="primary"
                  icon={<DownloadOutlined />}
                  loading={action.isPending}
                  onClick={() => downloadAttachment(attachment)}
                >
                  下载此附件
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<FileOutlined className="attachment-icon" />}
                title={attachment.name}
                description={`${attachment.type} / ${attachment.size} / 已下载 ${attachment.downloads} 次`}
              />
            </List.Item>
          )}
        />
      </section>

      <CommentPanel kind="resources" id={resource.id} comments={comments} />
      <ReportModal open={reportOpen} target="RESOURCE" targetId={resource.id} onClose={() => setReportOpen(false)} />
    </>
  );
}
