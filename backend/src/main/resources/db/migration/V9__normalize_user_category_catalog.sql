-- Normalize the user-facing category catalog shared by homepage, resources, demands, and demo data.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO resource_category (
    id, parent_id, category_name, level_no, status, sort_order
) VALUES
    (1, NULL, '文档资料', 1, 'ENABLED', 1),
    (2, NULL, '设计素材', 1, 'ENABLED', 2),
    (3, NULL, '源码模板', 1, 'ENABLED', 3),
    (4, NULL, '教程学习', 1, 'ENABLED', 4),
    (5, NULL, '软件工具', 1, 'ENABLED', 5),
    (11, 1, '考试资料', 2, 'ENABLED', 1),
    (12, 1, '办公模板', 2, 'ENABLED', 2),
    (13, 1, '学习笔记', 2, 'ENABLED', 3),
    (21, 2, 'UI设计', 2, 'ENABLED', 1),
    (22, 2, '图片素材', 2, 'ENABLED', 2),
    (23, 2, '字体图标', 2, 'ENABLED', 3),
    (31, 3, '前端源码', 2, 'ENABLED', 1),
    (32, 3, '后端源码', 2, 'ENABLED', 2),
    (33, 3, '完整项目', 2, 'ENABLED', 3),
    (41, 4, 'IT教程', 2, 'ENABLED', 1),
    (42, 4, '办公教程', 2, 'ENABLED', 2),
    (43, 4, '设计教程', 2, 'ENABLED', 3),
    (51, 5, '开发工具', 2, 'ENABLED', 1),
    (52, 5, '设计工具', 2, 'ENABLED', 2),
    (53, 5, '效率工具', 2, 'ENABLED', 3)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    category_name = VALUES(category_name),
    level_no = VALUES(level_no),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    deleted_at = NULL;
