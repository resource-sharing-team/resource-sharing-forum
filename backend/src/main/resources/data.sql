-- Resource Sharing Forum V2 seed data.
-- Password hashes are placeholders for development only.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO membership_level (
    id, level_code, level_name, min_points, max_points, daily_download_limit,
    max_files_per_resource, max_file_size_mb, reward_limit, can_apply_top, sort_order
) VALUES
    (1, 'NORMAL', '普通会员', 0, 99, 10, 5, 100, 100, 0, 1),
    (2, 'ACTIVE', '活跃会员', 100, 499, 20, 8, 150, 500, 0, 2),
    (3, 'QUALITY', '优质会员', 500, 1999, 50, 10, 200, 2000, 1, 3),
    (4, 'SENIOR', '资深会员', 2000, NULL, 100, 15, 500, 10000, 1, 4)
ON DUPLICATE KEY UPDATE
    level_name = VALUES(level_name),
    min_points = VALUES(min_points),
    max_points = VALUES(max_points),
    daily_download_limit = VALUES(daily_download_limit),
    max_files_per_resource = VALUES(max_files_per_resource),
    max_file_size_mb = VALUES(max_file_size_mb),
    reward_limit = VALUES(reward_limit),
    can_apply_top = VALUES(can_apply_top),
    sort_order = VALUES(sort_order);

INSERT INTO user_account (
    id, username, email, password_hash, role, status
) VALUES
    (1, 'demo_user', 'demo@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL'),
    (2, 'admin', 'admin@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'ADMIN', 'NORMAL')
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    status = VALUES(status);

INSERT INTO member_profile (
    id, account_id, nickname, avatar_url, gender, bio, school, major
) VALUES
    (1, 1, '考研资料君', NULL, 'UNKNOWN', '热爱分享学习资料。', '示例大学', '计算机科学与技术')
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    bio = VALUES(bio),
    school = VALUES(school),
    major = VALUES(major);

INSERT INTO administrator_profile (
    id, account_id, real_name, employee_no, permission_group, status
) VALUES
    (1, 2, '审核管理员', 'ADMIN-001', 'SUPER_ADMIN', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name),
    permission_group = VALUES(permission_group),
    status = VALUES(status);

INSERT INTO member_point_account (
    id, member_id, level_id, current_points, frozen_points, total_earned_points, total_spent_points
) VALUES
    (1, 1, 3, 650, 0, 650, 0)
ON DUPLICATE KEY UPDATE
    level_id = VALUES(level_id),
    current_points = VALUES(current_points),
    frozen_points = VALUES(frozen_points),
    total_earned_points = VALUES(total_earned_points),
    total_spent_points = VALUES(total_spent_points);

INSERT INTO resource_category (
    id, parent_id, category_name, level_no, sort_order
) VALUES
    (1, NULL, '文档资料', 1, 1),
    (2, NULL, '设计素材', 1, 2),
    (3, NULL, '源码模板', 1, 3),
    (4, NULL, '教程学习', 1, 4),
    (5, NULL, '软件工具', 1, 5),
    (11, 1, '考试资料', 2, 1),
    (12, 1, '办公模板', 2, 2),
    (21, 2, 'UI设计', 2, 1),
    (31, 3, '前端源码', 2, 1),
    (32, 3, '后端源码', 2, 2),
    (41, 4, '视频教程', 2, 1),
    (51, 5, '效率工具', 2, 1)
ON DUPLICATE KEY UPDATE
    category_name = VALUES(category_name),
    level_no = VALUES(level_no),
    sort_order = VALUES(sort_order);

INSERT INTO tag_info (
    id, tag_name, use_count
) VALUES
    (1, '考研', 1),
    (2, '政治', 1),
    (3, '真题', 1),
    (4, 'Java', 0),
    (5, 'SpringBoot', 0),
    (6, '前端', 0),
    (7, '模板', 0)
ON DUPLICATE KEY UPDATE
    use_count = VALUES(use_count);

INSERT INTO resource_info (
    id, publisher_id, category_id, title, resource_type, summary, description,
    external_url, status, view_count, download_count, favorite_count, like_count,
    comment_count, rating_count, average_rating, current_version_no, submitted_time, published_time
) VALUES (
    1, 1, 11, '2026考研政治历年真题完整版', 'DOCUMENT',
    '整理近年考研政治真题和答案解析，适合课程项目演示。',
    '该资源用于演示资源发布、审核通过、附件下载、收藏点赞、评论评分等核心流程。',
    NULL, 'PUBLISHED', 1286, 136, 23, 18, 0, 1, 4.80, 1, NOW(3), NOW(3)
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    description = VALUES(description),
    status = VALUES(status),
    view_count = VALUES(view_count),
    download_count = VALUES(download_count),
    favorite_count = VALUES(favorite_count),
    like_count = VALUES(like_count),
    rating_count = VALUES(rating_count),
    average_rating = VALUES(average_rating),
    published_time = VALUES(published_time);

INSERT INTO resource_version (
    id, resource_id, version_no, title, summary, description, category_id,
    resource_type, external_url, tag_snapshot, attachment_snapshot, submitter_id
) VALUES (
    1, 1, 1, '2026考研政治历年真题完整版',
    '整理近年考研政治真题和答案解析，适合课程项目演示。',
    '该资源用于演示资源发布、审核通过、附件下载、收藏点赞、评论评分等核心流程。',
    11, 'DOCUMENT', NULL,
    JSON_ARRAY('考研', '政治', '真题'),
    JSON_ARRAY(JSON_OBJECT('fileName', 'kaoyan-politics-2026.zip', 'fileSize', 2048000)),
    1
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    description = VALUES(description),
    tag_snapshot = VALUES(tag_snapshot),
    attachment_snapshot = VALUES(attachment_snapshot);

INSERT INTO resource_tag_rel (resource_id, tag_id)
VALUES
    (1, 1),
    (1, 2),
    (1, 3)
ON DUPLICATE KEY UPDATE
    resource_id = VALUES(resource_id);

INSERT INTO file_attachment (
    id, owner_type, owner_id, uploader_id, original_file_name, stored_file_name,
    file_ext, mime_type, file_size, file_hash, storage_path, status, download_count
) VALUES (
    1, 'RESOURCE', 1, 1, 'kaoyan-politics-2026.zip', 'resource-1-v1.zip',
    'zip', 'application/zip', 2048000, 'demo-resource-file-hash',
    './uploads/resource/1/resource-1-v1.zip', 'NORMAL', 136
)
ON DUPLICATE KEY UPDATE
    original_file_name = VALUES(original_file_name),
    stored_file_name = VALUES(stored_file_name),
    status = VALUES(status),
    download_count = VALUES(download_count);

INSERT INTO resource_audit_record (
    id, resource_id, version_no, auditor_id, audit_result, reason
) VALUES
    (1, 1, 1, 1, 'APPROVED', '示例资源审核通过。')
ON DUPLICATE KEY UPDATE
    audit_result = VALUES(audit_result),
    reason = VALUES(reason);

INSERT INTO resource_status_log (
    id, resource_id, from_status, to_status, operator_id, reason
) VALUES
    (1, 1, 'PENDING_REVIEW', 'PUBLISHED', 2, '示例资源审核通过。')
ON DUPLICATE KEY UPDATE
    to_status = VALUES(to_status),
    reason = VALUES(reason);

INSERT INTO resource_rating (
    id, member_id, resource_id, score, comment
) VALUES
    (1, 1, 1, 5, '示例评分记录。')
ON DUPLICATE KEY UPDATE
    score = VALUES(score),
    comment = VALUES(comment);

INSERT INTO request_post (
    id, requester_id, category_id, title, content, expected_format,
    reward_points, status, view_count, answer_count
) VALUES (
    1, 1, 32, '求一份Spring Boot后端项目模板',
    '需要一份适合课程设计使用的Spring Boot后端项目模板，最好包含登录鉴权、资源管理和基础测试。',
    'zip或Git仓库链接', 50, 'ONGOING', 32, 0
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    reward_points = VALUES(reward_points),
    status = VALUES(status);

INSERT INTO request_tag_rel (request_id, tag_id)
VALUES
    (1, 4),
    (1, 5)
ON DUPLICATE KEY UPDATE
    request_id = VALUES(request_id);

INSERT INTO request_status_log (
    id, request_id, from_status, to_status, operator_id, reason
) VALUES
    (1, 1, NULL, 'ONGOING', 1, '示例求资源帖创建。')
ON DUPLICATE KEY UPDATE
    to_status = VALUES(to_status),
    reason = VALUES(reason);

INSERT INTO notification_event (
    id, event_type, source_type, source_id, receiver_id, payload, status, process_time
) VALUES (
    1, 'RESOURCE_AUDIT_APPROVED', 'RESOURCE_AUDIT_RECORD', 1, 1,
    JSON_OBJECT('resourceId', 1, 'title', '2026考研政治历年真题完整版'),
    'SENT', NOW(3)
)
ON DUPLICATE KEY UPDATE
    payload = VALUES(payload),
    status = VALUES(status),
    process_time = VALUES(process_time);

INSERT INTO system_notice (
    id, event_id, receiver_id, notice_type, title, content, target_type, target_id, is_read
) VALUES (
    1, 1, 1, 'RESOURCE_AUDIT', '资源审核通过',
    '你发布的资源《2026考研政治历年真题完整版》已审核通过。',
    'RESOURCE', 1, 0
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    is_read = VALUES(is_read);

INSERT INTO system_config (
    id, config_key, config_value, value_type, description, is_sensitive, is_enabled
) VALUES
    (1, 'upload.allowed_types', 'pdf,doc,docx,ppt,pptx,xls,xlsx,zip,rar,7z,png,jpg,txt,md', 'STRING', 'Allowed upload extensions.', 0, 1),
    (2, 'upload.max_file_size_mb', '100', 'INTEGER', 'Default max file size in MB.', 0, 1),
    (3, 'resource.max_files_per_resource', '5', 'INTEGER', 'Default max files per resource.', 0, 1),
    (4, 'auth.max_failed_login_count', '5', 'INTEGER', 'Max failed login attempts before temporary lock.', 0, 1),
    (5, 'request.default_reward_limit', '100', 'INTEGER', 'Default reward limit for normal members.', 0, 1)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    value_type = VALUES(value_type),
    description = VALUES(description),
    is_enabled = VALUES(is_enabled);

INSERT INTO platform_announcement (
    id, title, content, status, publisher_id, publish_time
) VALUES (
    1, '资源分享论坛上线公告', '欢迎使用资源分享论坛课程项目演示环境。', 'PUBLISHED', 1, NOW(3)
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    status = VALUES(status),
    publish_time = VALUES(publish_time);
