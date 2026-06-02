INSERT INTO member_levels (id, level_name, min_points, daily_download_limit, max_files_per_resource, max_file_size_mb, reward_limit, can_apply_top, sort_order)
VALUES
    (1, '普通会员', 0, 10, 5, 100, 100, 0, 1),
    (2, '活跃会员', 100, 20, 8, 150, 500, 0, 2),
    (3, '优质会员', 500, 50, 10, 200, 2000, 1, 3),
    (4, '资深会员', 2000, 100, 15, 500, 10000, 1, 4)
ON DUPLICATE KEY UPDATE level_name = VALUES(level_name);

INSERT INTO categories (id, parent_id, name, sort_order)
VALUES
    (1, NULL, '文档资料', 1),
    (2, NULL, '设计素材', 2),
    (3, NULL, '源码模板', 3),
    (4, NULL, '教程学习', 4),
    (5, NULL, '软件工具', 5),
    (11, 1, '考试资料', 1),
    (12, 1, '办公模板', 2),
    (31, 3, '前端源码', 1),
    (32, 3, '后端源码', 2)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO users (id, username, email, password_hash, nickname, status, role, member_level_id, points)
VALUES
    (1, 'demo_user', 'demo@example.com', '$2a$10$placeholder', '考研资料君', 'NORMAL', 'USER', 3, 650),
    (2, 'admin', 'admin@example.com', '$2a$10$placeholder', '审核管理员', 'NORMAL', 'ADMIN', 4, 2000)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

