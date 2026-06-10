import { message } from 'antd';
import { type ChangeEvent, type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCategories, usePublishResource, useResourceTypes } from '../api/hooks';
import { InlineApiError } from '../components/ApiState';
import { resourcePublishSchema } from '../utils/validation';

export default function PublishResourcePage() {
  const navigate = useNavigate();
  const publish = usePublishResource();
  const categoriesQuery = useCategories();
  const resourceTypesQuery = useResourceTypes();
  const [files, setFiles] = useState<File[]>([]);
  const [feedback, setFeedback] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
  const [values, setValues] = useState({
    title: '',
    category1: '',
    category2: '',
    type: '',
    tags: '',
    description: '',
    detail: '',
  });
  const categories = categoriesQuery.data || [];
  const resourceTypes = resourceTypesQuery.data || [];
  const selectedCategory = categories.find((item) => item.id === values.category1);
  const tags = values.tags.split(/[,，\s]+/).filter(Boolean).slice(0, 5);

  const update = (key: keyof typeof values, value: string) => setValues((prev) => ({ ...prev, [key]: value }));

  function onFiles(event: ChangeEvent<HTMLInputElement>) {
    const next = Array.from(event.target.files || []).slice(0, 5);
    setFiles(next);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFeedback(null);
    const parsed = resourcePublishSchema.safeParse({ ...values, tags });
    if (!parsed.success) {
      const errorMessage = parsed.error.issues[0]?.message || '表单校验失败';
      setFeedback({ type: 'error', text: errorMessage });
      message.error(errorMessage);
      return;
    }
    const formData = new FormData();
    Object.entries(parsed.data).forEach(([key, value]) => {
      formData.append(key, Array.isArray(value) ? value.join(',') : String(value));
    });
    formData.append('categoryId', parsed.data.category2);
    formData.append('resourceType', parsed.data.type);
    formData.append('fileName', files[0]?.name || 'resource-file.zip');
    files.forEach((file) => formData.append('files', file));
    try {
      const resource = await publish.mutateAsync(formData);
      setFeedback({ type: 'success', text: `资源“${resource.title}”已提交审核，可在个人中心查看审核状态。` });
      message.success('资源已提交审核');
      window.setTimeout(() => navigate('/profile?tab=my-resource'), 700);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '接口调用失败';
      setFeedback({ type: 'error', text: errorMessage });
      message.error(errorMessage);
    }
  }

  return (
    <div className="container narrow">
      <div className="card">
        <div className="card-title">发布资源</div>
        <div className="card-body">
          {categoriesQuery.isError && <InlineApiError error={categoriesQuery.error} />}
          {resourceTypesQuery.isError && <InlineApiError error={resourceTypesQuery.error} />}
          <form onSubmit={submit}>
            <div className="form-item">
              <div className="form-label">资源标题</div>
              <input className="form-input" value={values.title} onChange={(event) => update('title', event.target.value)} placeholder="请输入资源标题（5-80字）" />
              <div className="tip">标题需清晰描述资源内容，便于搜索</div>
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
              <div className="form-label">资源类型</div>
              <select className="form-input" value={values.type} onChange={(event) => update('type', event.target.value)}>
                <option value="">请选择资源类型</option>
                {resourceTypes.map((type) => (
                  <option value={type.value} key={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
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
              <div className="tip">标签用于精准搜索，建议使用3-5个</div>
            </div>

            <div className="form-item">
              <div className="form-label">资源简介</div>
              <textarea className="form-textarea" value={values.description} onChange={(event) => update('description', event.target.value)} placeholder="简要介绍资源内容、用途、亮点（20-200字）" />
            </div>

            <div className="form-item">
              <div className="form-label">资源详细说明</div>
              <textarea className="form-content" value={values.detail} onChange={(event) => update('detail', event.target.value)} placeholder="详细介绍资源内容、使用方法、适用人群等" />
            </div>

            <div className="form-item">
              <div className="form-label">上传附件</div>
              <div className="upload-area">
                <label className="upload-btn">
                  选择文件上传
                  <input type="file" multiple hidden onChange={onFiles} />
                </label>
                <div className="upload-tip">支持pdf/doc/zip/rar/png等，单个≤100MB，最多上传5个</div>
              </div>
              <div className="file-list">
                {files.map((file) => (
                  <div className="file-item" key={file.name}>
                    <div>
                      <div className="file-name">{file.name}</div>
                      <div className="file-size">{Math.max(1, Math.round(file.size / 1024))}KB</div>
                    </div>
                    <button className="file-del" type="button" onClick={() => setFiles((prev) => prev.filter((item) => item !== file))}>
                      删除
                    </button>
                  </div>
                ))}
              </div>
              <div className="tip">根据您的会员等级，当前最多可上传5个附件</div>
            </div>

            <div className="btn-bar">
              <button className="btn-cancel" type="button" onClick={() => navigate(-1)}>
                取消
              </button>
              <button className="btn-submit" type="submit" disabled={publish.isPending}>
                {publish.isPending ? '提交中...' : '提交审核'}
              </button>
            </div>
            {feedback && <div className={`form-feedback ${feedback.type}`}>{feedback.text}</div>}
            <div className="tip" style={{ textAlign: 'center', marginTop: 12 }}>提交后进入待审核状态，审核通过后公开可见</div>
          </form>
        </div>
      </div>
    </div>
  );
}
