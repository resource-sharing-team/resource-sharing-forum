import { message } from 'antd';
import { useEffect, useState } from 'react';
import { useReportContent } from '../api/hooks';
import type { ReportTarget } from '../types';

type Props = {
  open: boolean;
  target: ReportTarget;
  targetId: number;
  onClose: () => void;
};

export default function ReportModal({ open, target, targetId, onClose }: Props) {
  const report = useReportContent();
  const [type, setType] = useState(target);
  const [reason, setReason] = useState('');
  const [proof, setProof] = useState('');

  useEffect(() => {
    if (open) {
      setType(target);
      setReason('');
      setProof('');
    }
  }, [open, target]);

  if (!open) return null;

  async function submit() {
    if (reason.trim().length < 10) {
      message.warning('举报原因至少 10 个字');
      return;
    }
    try {
      await report.mutateAsync({ target, targetId, type, reason: `${reason}${proof ? `\n版权证明：${proof}` : ''}` });
      message.success('已提交，我们将尽快处理');
      onClose();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '接口调用失败');
    }
  }

  return (
    <div className="modal">
      <div className="modal-content">
        <div className="modal-title">我要举报/投诉</div>
        <div className="form-item">
          <div className="form-label">举报类型</div>
          <select className="form-input" value={type} onChange={(event) => setType(event.target.value as ReportTarget)}>
            <option value="RESOURCE">资源举报</option>
            <option value="DEMAND">求资源举报</option>
            <option value="COMMENT">评论举报</option>
            <option value="COPYRIGHT">版权投诉</option>
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
