import { z } from 'zod';

export const resourcePublishSchema = z.object({
  title: z.string().min(5, '标题至少 5 个字').max(80, '标题最多 80 个字'),
  category1: z.string().min(1, '请选择一级分类'),
  category2: z.string().min(1, '请选择二级分类'),
  type: z.string().min(1, '请选择资源类型'),
  tags: z.array(z.string()).min(1, '至少添加 1 个标签').max(5, '最多 5 个标签'),
  description: z.string().min(20, '简介至少 20 个字').max(200, '简介最多 200 个字'),
  detail: z.string().min(20, '详细说明至少 20 个字'),
});

export const demandPublishSchema = z.object({
  title: z.string().min(5, '标题至少 5 个字').max(80, '标题最多 80 个字'),
  category1: z.string().min(1, '请选择一级分类'),
  category2: z.string().min(1, '请选择二级分类'),
  tags: z.array(z.string()).min(1, '至少添加 1 个标签').max(5, '最多 5 个标签'),
  description: z.string().min(20, '需求说明至少 20 个字').max(500, '需求说明最多 500 个字'),
  format: z.string().min(1, '请填写期望格式'),
  points: z.coerce.number().min(0, '悬赏积分不能小于 0').max(1000, '悬赏积分最多 1000'),
});
