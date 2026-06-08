import { Form, Input, Modal, Select, message } from 'antd';
import { useEffect } from 'react';
import { useReportContent } from '../api/hooks';
import type { ReportTarget } from '../types';

type Props = {
  open: boolean;
  target: ReportTarget;
  targetId: number;
  onClose: () => void;
};

export default function ReportModal({ open, target, targetId, onClose }: Props) {
  const [form] = Form.useForm();
  const report = useReportContent();

  useEffect(() => {
    if (open) form.resetFields();
  }, [form, open]);

  return (
    <Modal
      title={target === 'COPYRIGHT' ? '版权投诉' : '内容举报'}
      open={open}
      confirmLoading={report.isPending}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="提交"
      cancelText="取消"
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          await report.mutateAsync({ target, targetId, type: values.type, reason: values.reason });
          message.success('已提交，管理员会尽快处理');
          onClose();
        }}
      >
        <Form.Item name="type" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
          <Select
            options={[
              { value: 'copyright', label: '版权/侵权' },
              { value: 'spam', label: '广告或灌水' },
              { value: 'illegal', label: '违规内容' },
              { value: 'quality', label: '资源失效或描述不符' },
            ]}
          />
        </Form.Item>
        <Form.Item name="reason" label="说明" rules={[{ required: true, message: '请输入说明' }, { min: 10, message: '至少输入 10 个字' }]}>
          <Input.TextArea rows={4} placeholder="请说明问题、链接、权属证明或具体位置" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
