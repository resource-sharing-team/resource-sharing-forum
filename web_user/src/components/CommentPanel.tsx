import { SendOutlined } from '@ant-design/icons';
import { Button, Form, Input, Tag, message } from 'antd';
import { useAddComment } from '../api/hooks';
import type { Comment } from '../types';

type Props = {
  kind: 'resources' | 'demands';
  id: number;
  comments: Comment[];
};

export default function CommentPanel({ kind, id, comments }: Props) {
  const [form] = Form.useForm<{ content: string }>();
  const addComment = useAddComment(kind, id);

  return (
    <section className="card comment-panel">
      <div className="card-title">{kind === 'resources' ? '评论与回复' : '回答列表'}</div>
      <div className="card-body">
      <Form
        form={form}
        onFinish={async ({ content }) => {
          await addComment.mutateAsync(content);
          form.resetFields();
          message.success(kind === 'resources' ? '评论已发布' : '回答已发布');
        }}
      >
        <Form.Item name="content" rules={[{ required: true, message: '请输入内容' }, { min: 5, message: '至少输入 5 个字' }]}>
          <Input.TextArea className="comment-input" rows={4} placeholder={kind === 'resources' ? '请输入评论内容' : '分享资源链接、附件说明或解决思路'} />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={addComment.isPending}>
          {kind === 'resources' ? '发表评论' : '提交回答'}
        </Button>
      </Form>

      <div style={{ marginTop: 14 }}>
        {comments.map((item) => (
          <div className="comment-item" key={item.id}>
            <div className="comment-user">
              <span className="comment-avatar">{item.author.slice(0, 1)}</span>
              <span className="comment-name">{item.author}</span>
              {item.mine && <Tag color="green">我</Tag>}
              {item.accepted && <Tag color="blue">已采纳</Tag>}
              <span className="comment-date">{item.date}</span>
            </div>
            <div className="comment-content">{item.content}</div>
            {!!item.replies?.length && (
              <div className="reply-box">
                {item.replies.map((reply) => (
                  <div className="reply-item" key={reply.id}>
                    <strong>{reply.author}：</strong>
                    {reply.content}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
      </div>
    </section>
  );
}
