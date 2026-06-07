import { message } from 'antd';
import { useState } from 'react';
import { useAddComment, useDeleteComment } from '../api/hooks';
import type { Comment } from '../types';
import ReportModal from './ReportModal';

type Props = {
  kind: 'resources' | 'demands';
  id: number;
  comments: Comment[];
  title?: string;
  ownerName?: string;
};

type ReportState = {
  id: number;
};

export default function CommentPanel({ kind, id, comments, title, ownerName }: Props) {
  const [content, setContent] = useState('');
  const [replyingTo, setReplyingTo] = useState<Comment | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [reportTarget, setReportTarget] = useState<ReportState | null>(null);
  const addComment = useAddComment(kind, id);
  const deleteComment = useDeleteComment(kind, id);
  const isResource = kind === 'resources';

  async function submit() {
    if (content.trim().length < 5) {
      message.warning('至少输入 5 个字');
      return;
    }
    try {
      await addComment.mutateAsync({ content: content.trim() });
      setContent('');
      message.success(isResource ? '评论已发布' : '回答已发布');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function submitReply(parent: Comment) {
    if (replyContent.trim().length < 2) {
      message.warning('回复至少输入 2 个字');
      return;
    }
    try {
      await addComment.mutateAsync({ content: replyContent.trim(), parentId: parent.id });
      setReplyingTo(null);
      setReplyContent('');
      message.success('回复已发布');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  async function removeComment(comment: Comment) {
    try {
      await deleteComment.mutateAsync(comment.id);
      message.success('评论已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  function isOwner(author: string) {
    return Boolean(ownerName && author === ownerName);
  }

  function reportComment(comment: Comment) {
    setReportTarget({ id: comment.id });
  }

  return (
    <div className="card">
      <div className="card-title">{title || (isResource ? '评论与回复' : '回答列表')}</div>
      <div className="card-body">
        <textarea
          className="comment-input"
          placeholder={isResource ? '请输入评论内容' : '请输入你的回答，可以分享资源、链接或说明'}
          value={content}
          onChange={(event) => setContent(event.target.value)}
        />
        <div className="comment-submit-row">
          <button className="btn-primary" onClick={submit} disabled={addComment.isPending}>
            {isResource ? '发表评论' : '提交回答'}
          </button>
        </div>

        <div className="comment-list">
          {comments.map((item) => (
            <div className="comment-item" key={item.id}>
              <div className="comment-actions">
                {item.mine && (
                  <button className="comment-delete" onClick={() => removeComment(item)} disabled={deleteComment.isPending}>
                    删除
                  </button>
                )}
                <button className="comment-reply" onClick={() => setReplyingTo(item)}>
                  回复
                </button>
                <button className="comment-report" onClick={() => reportComment(item)}>
                  举报
                </button>
              </div>

              <div className="comment-user">
                <div className="comment-avatar">{item.author.slice(0, 1)}</div>
                <div className="comment-name">{item.author}</div>
                {isOwner(item.author) && <span className="comment-role">发布者</span>}
                {item.accepted && <span className="comment-accepted">已采纳</span>}
                <span className="comment-date">{item.date}</span>
              </div>
              <div className="comment-content">{item.content}</div>

              {replyingTo?.id === item.id && (
                <div className="reply-editor">
                  <div className="reply-editor-title">回复 {item.author}</div>
                  <textarea
                    className="reply-input"
                    value={replyContent}
                    onChange={(event) => setReplyContent(event.target.value)}
                    placeholder={`回复 ${item.author}，内容会显示在这条评论下方`}
                  />
                  <div className="reply-editor-actions">
                    <button
                      className="btn-cancel"
                      onClick={() => {
                        setReplyingTo(null);
                        setReplyContent('');
                      }}
                    >
                      取消
                    </button>
                    <button className="btn-primary" onClick={() => submitReply(item)} disabled={addComment.isPending}>
                      发布回复
                    </button>
                  </div>
                </div>
              )}

              {item.replies?.length ? (
                <div className="reply-box">
                  {item.replies.map((reply) => (
                    <div className="reply-item" key={reply.id}>
                      <div className="reply-line">
                        <span className="reply-author">{reply.author}</span>
                        {isOwner(reply.author) && <span className="comment-role">发布者</span>}
                        <span className="reply-target">回复 {reply.replyToAuthor || item.author}</span>
                        <span className="reply-date">{reply.date}</span>
                        <button className="reply-report" onClick={() => reportComment(reply)}>
                          举报
                        </button>
                      </div>
                      <div className="reply-content">{reply.content}</div>
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ))}
          {!comments.length && <div className="tip empty-comments">暂无评论，来发布第一条吧</div>}
        </div>
      </div>

      <ReportModal open={Boolean(reportTarget)} target="COMMENT" targetId={reportTarget?.id || 0} onClose={() => setReportTarget(null)} />
    </div>
  );
}
