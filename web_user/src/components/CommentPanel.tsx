import { SendOutlined } from '@ant-design/icons';
import { Avatar, Button, Form, Input, List, Space, Tag, Typography, message } from 'antd';
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
    <section style={{ marginTop: 20 }}>
      <div className="section-head">
        <div>
          <p className="section-kicker">DISCUSSION</p>
          <h2 className="section-title">{kind === 'resources' ? '评论区' : '回答区'}</h2>
        </div>
      </div>
      <Form
        form={form}
        onFinish={async ({ content }) => {
          await addComment.mutateAsync(content);
          form.resetFields();
          message.success(kind === 'resources' ? '评论已发布' : '回答已发布');
        }}
      >
        <Form.Item name="content" rules={[{ required: true, message: '请输入内容' }, { min: 5, message: '至少输入 5 个字' }]}>
          <Input.TextArea rows={4} placeholder={kind === 'resources' ? '写下你的使用反馈或补充说明' : '分享资源链接、附件说明或解决思路'} />
        </Form.Item>
        <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={addComment.isPending}>
          发布
        </Button>
      </Form>

      <List
        style={{ marginTop: 18 }}
        dataSource={comments}
        itemLayout="vertical"
        renderItem={(item) => (
          <List.Item>
            <List.Item.Meta
              avatar={<Avatar>{item.author.slice(0, 1)}</Avatar>}
              title={
                <Space>
                  <Typography.Text strong>{item.author}</Typography.Text>
                  {item.mine && <Tag color="green">我</Tag>}
                  {item.accepted && <Tag color="blue">已采纳</Tag>}
                  <Typography.Text type="secondary">{item.date}</Typography.Text>
                </Space>
              }
              description={item.content}
            />
            {item.replies?.map((reply) => (
              <div key={reply.id} style={{ marginLeft: 44, marginTop: 8, color: '#66746c' }}>
                <Typography.Text strong>{reply.author}：</Typography.Text>
                {reply.content}
              </div>
            ))}
          </List.Item>
        )}
      />
    </section>
  );
}
