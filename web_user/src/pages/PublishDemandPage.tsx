import { message } from 'antd';
import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCategories, usePublishDemand } from '../api/hooks';
import { InlineApiError } from '../components/ApiState';
import { demandPublishSchema } from '../utils/validation';

export default function PublishDemandPage() {
  const navigate = useNavigate();
  const publish = usePublishDemand();
  const categoriesQuery = useCategories();
  const [rewardType, setRewardType] = useState<'free' | 'point'>('free');
  const [feedback, setFeedback] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
  const [values, setValues] = useState({
    title: '',
    category1: '',
    category2: '',
    tags: '',
    description: '',
    format: '',
    points: '0',
  });
  const categories = categoriesQuery.data || [];
  const selectedCategory = categories.find((item) => item.id === values.category1);
  const tags = values.tags.split(/[,，\s]+/).filter(Boolean).slice(0, 5);

  const update = (key: keyof typeof values, value: string) => setValues((prev) => ({ ...prev, [key]: value }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFeedback(null);
    const payload = {
      ...values,
      points: rewardType === 'point' ? Number(values.points || 0) : 0,
      tags,
    };
    const parsed = demandPublishSchema.safeParse(payload);
    if (!parsed.success) {
      const errorMessage = parsed.error.issues[0]?.message || '表单校验失败';
      setFeedback({ type: 'error', text: errorMessage });
      message.error(errorMessage);
      return;
    }
    try {
      const demand = await publish.mutateAsync(parsed.data);
      setFeedback({ type: 'success', text: `求资源“${demand.title}”已发布。` });
      message.success('求资源已发布');
      window.setTimeout(() => navigate(`/demands/${demand.id}`), 500);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '接口调用失败';
      setFeedback({ type: 'error', text: errorMessage });
      message.error(errorMessage);
    }
  }

  return (
    <div className="container narrow">
      <div className="card">
        <div className="card-title">发布求资源</div>
        <div className="card-body">
          {categoriesQuery.isError && <InlineApiError error={categoriesQuery.error} />}
          <form onSubmit={submit}>
            <div className="form-item">
              <div className="form-label">求资源标题</div>
              <input className="form-input" value={values.title} onChange={(event) => update('title', event.target.value)} placeholder="5-80字，清晰描述你需要的资源" />
              <div className="tip">例：求2026教资面试真题、求Python实战项目源码</div>
            </div>

            <div className="category-row">
              <div className="form-item">
                <div className="form-label">一级分类</div>
                <select className="form-input" value={values.category1} onChange={(event) => setValues((prev) => ({ ...prev, category1: event.target.value, category2: '' }))}>
                  <option value="">请选择一级分类</option>
                  {categories.map((category) => (
                    <option value={category.id} key={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-item">
                <div className="form-label">二级分类</div>
                <select className="form-input" value={values.category2} onChange={(event) => update('category2', event.target.value)}>
                  <option value="">请先选择一级分类</option>
                  {selectedCategory?.children.map((category) => (
                    <option value={category.id} key={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="form-item">
              <div className="form-label">资源标签（1-5个，空格或逗号分隔）</div>
              <input className="tag-input" value={values.tags} onChange={(event) => update('tags', event.target.value)} placeholder="输入标签，最多5个" />
              <div className="tag-area">
                {tags.map((tag) => (
                  <span className="tag-item" key={tag}>
                    {tag} ×
                  </span>
                ))}
              </div>
            </div>

            <div className="form-item">
              <div className="form-label">需求说明</div>
              <textarea className="form-textarea" value={values.description} onChange={(event) => update('description', event.target.value)} placeholder="20-500字，详细说明你需要的资源内容、用途、版本要求等" />
            </div>

            <div className="form-item">
              <div className="form-label">期望格式</div>
              <input className="form-input" value={values.format} onChange={(event) => update('format', event.target.value)} placeholder="例：PDF、Word、PPT、源码、MP4、图片等" />
            </div>

            <div className="form-item">
              <div className="form-label">悬赏设置</div>
              <div className="publish-type">
                <label className="radio-item">
                  <input type="radio" name="publishType" value="free" checked={rewardType === 'free'} onChange={() => setRewardType('free')} />
                  <span>免费求资源</span>
                </label>
                <label className="radio-item">
                  <input type="radio" name="publishType" value="point" checked={rewardType === 'point'} onChange={() => setRewardType('point')} />
                  <span>积分悬赏</span>
                </label>
              </div>
              {rewardType === 'point' && (
                <input className="form-input" value={values.points} onChange={(event) => update('points', event.target.value)} placeholder="请输入悬赏积分（0~可用积分1000）" />
              )}
              <div className="tip">积分悬赏将冻结对应积分，采纳后发放给回答者</div>
            </div>

            <div className="btn-bar">
              <button className="btn-cancel" type="button" onClick={() => navigate(-1)}>
                取消
              </button>
              <button className="btn-submit" type="submit" disabled={publish.isPending}>
                {publish.isPending ? '发布中...' : '发布求资源'}
              </button>
            </div>
            {feedback && <div className={`form-feedback ${feedback.type}`}>{feedback.text}</div>}
            <div className="tip" style={{ textAlign: 'center', marginTop: 12 }}>发布后可在个人中心查看，未采纳前可取消</div>
          </form>
        </div>
      </div>
    </div>
  );
}
