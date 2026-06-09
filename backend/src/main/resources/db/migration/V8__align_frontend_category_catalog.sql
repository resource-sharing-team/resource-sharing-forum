-- Align category names used by web_user filters, homepage recommendations, and demo data.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO resource_category (
    id, parent_id, category_name, level_no, status, sort_order
) VALUES
    (21, 2, 'UI设计', 2, 'ENABLED', 1),
    (22, 2, '图片素材', 2, 'ENABLED', 2),
    (41, 4, 'IT教程', 2, 'ENABLED', 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    category_name = VALUES(category_name),
    level_no = VALUES(level_no),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    deleted_at = NULL;
