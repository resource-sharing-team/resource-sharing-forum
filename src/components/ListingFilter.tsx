import { useEffect, useState } from 'react';
import { InlineApiError } from './ApiState';
import type { Category, ListParams, ResourceTypeOption } from '../types';

type Props = {
  mode: 'resources' | 'demands';
  value: ListParams;
  categories: Category[];
  categoriesError?: unknown;
  resourceTypes?: ResourceTypeOption[];
  resourceTypesError?: unknown;
  onChange: (next: ListParams) => void;
};

export default function ListingFilter({ mode, value, categories, categoriesError, resourceTypes = [], resourceTypesError, onChange }: Props) {
  const [keyword, setKeyword] = useState(value.keyword || '');
  const selectedCategory = categories.find((item) => item.id === value.cate1);
  const update = (patch: ListParams) => onChange({ ...value, ...patch, page: 1 });

  useEffect(() => {
    setKeyword(value.keyword || '');
  }, [value.keyword]);

  return (
    <div className="card">
      <div className="card-title">筛选条件</div>
      <div className="card-body">
        {categoriesError ? <InlineApiError error={categoriesError} /> : null}
        {mode === 'resources' && resourceTypesError ? <InlineApiError error={resourceTypesError} /> : null}
        <div className={mode === 'resources' ? 'filter-line' : 'filter-row'}>
          <div className={mode === 'resources' ? 'filter-search-item' : 'filter-item'}>
            <div className="filter-label">关键词</div>
            <div className="search-input-group">
              <input
                value={keyword}
                placeholder={mode === 'resources' ? '搜索标题、简介、标签、发布者' : '搜索标题、需求、标签、发布者'}
                onChange={(event) => setKeyword(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') update({ keyword });
                }}
              />
              <button type="button" onClick={() => update({ keyword })}>
                搜索
              </button>
            </div>
          </div>

          <div className="filter-item">
            <div className="filter-label">一级分类</div>
            <select className="filter-select" value={value.cate1 || ''} onChange={(event) => update({ cate1: event.target.value || undefined, cate2: undefined })}>
              <option value="">全部</option>
              {categories.map((item) => (
                <option value={item.id} key={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-item">
            <div className="filter-label">二级分类</div>
            <select className="filter-select" value={value.cate2 || ''} onChange={(event) => update({ cate2: event.target.value || undefined })}>
              <option value="">{selectedCategory ? '全部' : '请先选择一级分类'}</option>
              {selectedCategory?.children.map((item) => (
                <option value={item.id} key={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>

          {mode === 'resources' ? (
            <div className="filter-item">
              <div className="filter-label">资源类型</div>
              <select className="filter-select" value={value.type || ''} onChange={(event) => update({ type: event.target.value || undefined })}>
                <option value="">全部</option>
                {resourceTypes.map((item) => (
                  <option value={item.value} key={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <>
              <div className="filter-item">
                <div className="filter-label">悬赏积分</div>
                <select className="filter-select" value={value.type || ''} onChange={(event) => update({ type: event.target.value || undefined })}>
                  <option value="">全部</option>
                  <option value="free">免费</option>
                  <option value="0-100">0-100</option>
                  <option value="100-500">100-500</option>
                  <option value="500-2000">500-2000</option>
                  <option value="2000+">2000以上</option>
                </select>
              </div>
              <div className="filter-item">
                <div className="filter-label">状态</div>
                <select className="filter-select" value={value.status || ''} onChange={(event) => update({ status: event.target.value || undefined })}>
                  <option value="">全部</option>
                  <option value="active">进行中</option>
                  <option value="solved">已解决</option>
                </select>
              </div>
              <div className="filter-item">
                <div className="filter-label">排序</div>
                <select className="filter-select" value={value.sort || 'latest'} onChange={(event) => update({ sort: event.target.value })}>
                  <option value="latest">发布时间倒序</option>
                  <option value="reply">回复量倒序</option>
                  <option value="points">悬赏积分倒序</option>
                </select>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
