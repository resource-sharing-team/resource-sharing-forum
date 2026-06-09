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
  rewardType: z.enum(['FREE', 'POINT']).optional(),
  rewardPoints: z.coerce.number().optional(),
  points: z.coerce.number().optional(),
}).superRefine((value, context) => {
  const requestedPoints = Number(value.rewardPoints ?? value.points ?? 0);
  const rewardType = value.rewardType || (requestedPoints > 0 ? 'POINT' : 'FREE');
  if (requestedPoints < 0) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['rewardPoints'], message: '悬赏积分不能小于 0' });
  }
  if (rewardType === 'FREE' && requestedPoints > 0 && value.rewardType === 'FREE') {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['rewardPoints'], message: '免费求资源不能设置悬赏积分' });
  }
  if (rewardType === 'POINT' && requestedPoints < 1) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['rewardPoints'], message: '积分悬赏至少 1 分' });
  }
});
