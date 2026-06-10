import { message } from 'antd';
import { useEffect, useState } from 'react';
import { useReportContent } from '../api/hooks';
import type { ReportTarget } from '../types';

type Props = {
  open: boolean;
  target: ReportTarget;
  targetId: number;
  subjectTitle?: string;
  contactEmail?: string;
  onClose: () => void;
};

export default function ReportModal({ open, target, targetId, subjectTitle, contactEmail, onClose }: Props) {
  const report = useReportContent();
  const [type, setType] = useState(target);
  const [reason, setReason] = useState('');
  const [proof, setProof] = useState('');
  const [feedback, setFeedback] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  useEffect(() => {
    if (open) {
      setType(target);
      setReason('');
      setProof('');
      setFeedback(null);
    }
  }, [open, target]);

  if (!open) return null;
  const options = reportOptions(target);

  async function submit() {
    setFeedback(null);
    if (reason.trim().length < 10) {
      setFeedback({ type: 'error', text: '举报原因至少 10 个字，请补充具体情况后再提交。' });
      return;
    }
    try {
      await report.mutateAsync({
        target,
        targetId,
        type,
        title: reportTitle(type, subjectTitle, targetId),
        reason: reason.trim(),
        proofSummary: (proof || reason).trim(),
        contactEmail,
      });
      setFeedback({ type: 'success', text: '已提交，我们将尽快处理。' });
      message.success('已提交，我们将尽快处理');
      window.setTimeout(onClose, 600);
    } catch (error) {
      const errorMessage = reportErrorMessage(error instanceof Error ? error.message : '接口调用失败');
      setFeedback({ type: 'error', text: errorMessage });
      message.error(errorMessage);
    }
  }

  return (
    <div className="modal">
      <div className="modal-content">
        <div className="modal-title">我要举报/投诉</div>
        <div className="form-item">
          <div className="form-label">举报类型</div>
          <select className="form-input" value={type} onChange={(event) => setType(event.target.value as ReportTarget)}>
            {options.map((option) => (
              <option value={option.value} key={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="form-item">
          <div className="form-label">举报原因（10~300字）</div>
          <textarea className="form-textarea" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="请详细说明违规、侵权或不实内容" />
        </div>
        {type === 'COPYRIGHT' && (
          <div className="form-item">
            <div className="form-label">版权证明（选填）</div>
            <textarea className="form-textarea" value={proof} onChange={(event) => setProof(event.target.value)} placeholder="请填写权属说明、联系方式等" />
          </div>
        )}
        {feedback && <div className={`modal-feedback ${feedback.type}`}>{feedback.text}</div>}
        <div className="modal-buttons">
          <button className="btn-cancel" onClick={onClose}>
            取消
          </button>
          <button className="btn-primary" onClick={submit} disabled={report.isPending}>
            提交
          </button>
        </div>
      </div>
    </div>
  );
}

function reportTitle(type: ReportTarget | string, subjectTitle: string | undefined, targetId: number) {
  const typeText: Record<string, string> = {
    RESOURCE: '资源举报',
    DEMAND: '求资源举报',
    COMMENT: '评论举报',
    COPYRIGHT: '版权投诉',
  };
  const prefix = typeText[type] || '内容举报';
  const subject = subjectTitle?.trim() || `#${targetId}`;
  return `${prefix}：${subject}`.slice(0, 100);
}

function reportOptions(target: ReportTarget) {
  if (target === 'RESOURCE') {
    return [
      { value: 'RESOURCE', label: '资源举报' },
      { value: 'COPYRIGHT', label: '版权投诉' },
    ];
  }
  if (target === 'COPYRIGHT') {
    return [{ value: 'COPYRIGHT', label: '版权投诉' }];
  }
  if (target === 'DEMAND') {
    return [{ value: 'DEMAND', label: '求资源举报' }];
  }
  return [{ value: 'COMMENT', label: '评论举报' }];
}

function reportErrorMessage(message: string) {
  if (message.includes('/api/reports') && message.includes('HTTP 500') && message.includes('系统繁忙')) {
    return '后端返回 500：可能已经提交过同一目标的同类型举报/投诉，后端当前把重复举报唯一键冲突包装成“系统繁忙”。';
  }
  return message;
}
