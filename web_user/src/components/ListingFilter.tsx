import { useEffect, useState } from 'react';
import { categories, resourceTypes } from '../data/catalog';
import type { ListParams } from '../types';

type Props = {
  mode: 'resources' | 'demands';
  value: ListParams;
  onChange: (next: ListParams) => void;
};

export default function ListingFilter({ mode, value, onChange }: Props) {
  const [keyword, setKeyword] = useState(value.keyword || '');
  const selectedCategory = categories.find((item) => item.id === value.cate1);
  const update = (patch: ListParams) => onChange({ ...value, ...patch, page: 1 });
  const search = () => update({ keyword: keyword.trim() || undefined });

  useEffect(() => {
    setKeyword(value.keyword || '');
  }, [value.keyword]);

  return (
    <div className="filter-strip card">
      <div className="card-title">筛选条件</div>
      <div className="card-body">
        <div className={mode === 'resources' ? 'filter-line' : 'filter-row'}>
          <div className="filter-search-item">
            <div className="filter-label">关键词</div>
            <div className="search-input-group">
              <input
                value={keyword}
                placeholder={mode === 'resources' ? '搜索标题、简介、标签、发布者' : '搜索标题、需求、标签、发布者'}
                onChange={(event) => setKeyword(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') search();
                }}
              />
              <button type="button" onClick={search}>
                搜索
              </button>
            </div>
          </div>

          <label className="filter-item">
            <span className="filter-label">一级分类</span>
            <select className="filter-select" value={value.cate1 || ''} onChange={(event) => update({ cate1: event.target.value || undefined, cate2: undefined })}>
              <option value="">全部</option>
              {categories.map((item) => (
                <option value={item.id} key={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>

          <label className="filter-item">
            <span className="filter-label">二级分类</span>
            <select
              className="filter-select"
              value={value.cate2 || ''}
              disabled={!selectedCategory}
              onChange={(event) => update({ cate2: event.target.value || undefined })}
            >
              <option value="">{selectedCategory ? '全部' : '请先选择一级分类'}</option>
              {selectedCategory?.children.map((item) => (
                <option value={item.id} key={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>

          {mode === 'resources' ? (
            <label className="filter-item">
              <span className="filter-label">资源类型</span>
              <select className="filter-select" value={value.type || ''} onChange={(event) => update({ type: event.target.value || undefined })}>
                <option value="">全部</option>
                {resourceTypes.map((item) => (
                  <option value={item} key={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <>
              <label className="filter-item">
                <span className="filter-label">悬赏积分</span>
                <select className="filter-select" value={value.points || ''} onChange={(event) => update({ points: event.target.value || undefined })}>
                  <option value="">全部</option>
                  <option value="free">免费</option>
                  <option value="0-100">0-100</option>
                  <option value="100-500">100-500</option>
                  <option value="500-2000">500-2000</option>
                  <option value="2000+">2000以上</option>
                </select>
              </label>

              <label className="filter-item">
                <span className="filter-label">状态</span>
                <select className="filter-select" value={value.status || ''} onChange={(event) => update({ status: event.target.value || undefined })}>
                  <option value="">全部</option>
                  <option value="active">进行中</option>
                  <option value="solved">已解决</option>
                </select>
              </label>

              <label className="filter-item">
                <span className="filter-label">排序</span>
                <select className="filter-select" value={value.sort || 'latest'} onChange={(event) => update({ sort: event.target.value || 'latest' })}>
                  <option value="latest">发布时间</option>
                  <option value="reply">回复量</option>
                  <option value="points">悬赏积分</option>
                </select>
              </label>

              <label className="filter-item">
                <span className="filter-label">排序方式</span>
                <select className="filter-select" value={value.order || 'desc'} onChange={(event) => update({ order: event.target.value || 'desc' })}>
                  <option value="desc">倒序</option>
                  <option value="asc">正序</option>
                </select>
              </label>
            </>
          )}

          <button type="button" className="text-btn" onClick={() => onChange({ page: 1, pageSize: value.pageSize || 5, sort: 'latest', order: 'desc' })}>
            重置筛选
          </button>
        </div>
      </div>
    </div>
  );
}
