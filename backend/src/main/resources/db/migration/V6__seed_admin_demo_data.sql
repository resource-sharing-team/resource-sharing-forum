-- Admin console demo data for real backend integration.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

ALTER TABLE resource_info DROP CHECK ck_resource_status;
ALTER TABLE resource_info
    ADD CONSTRAINT ck_resource_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'REVIEWING_RISK', 'OFFLINE', 'COPYRIGHT_DOWN', 'DELETED'));

ALTER TABLE resource_audit_record DROP CHECK ck_audit_result;
ALTER TABLE resource_audit_record
    ADD CONSTRAINT ck_audit_result CHECK (audit_result IN ('APPROVED', 'REJECTED', 'OFFLINE', 'RESTORED', 'RISK_REVIEW', 'COPYRIGHT_DOWN', 'DELETED'));

INSERT INTO user_account (
    id, username, email, password_hash, role, status, failed_login_count, locked_until
) VALUES
    (3, 'user001', 'user001@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (4, 'user005', 'user005@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (5, 'user003', 'user003@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (6, 'disabled_user', 'disabled@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'DISABLED', 0, NULL),
    (7, 'locked_user', 'locked@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'LOCKED', 5, DATE_ADD(NOW(3), INTERVAL 10 MINUTE))
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    status = VALUES(status),
    failed_login_count = VALUES(failed_login_count),
    locked_until = VALUES(locked_until);

INSERT INTO member_profile (
    id, account_id, nickname, avatar_url, gender, bio, school, major
) VALUES
    (2, 3, '清风徐来', NULL, 'UNKNOWN', '偏好分享设计素材和课程模板。', '示例大学', '数字媒体技术'),
    (3, 4, '办公达人', NULL, 'UNKNOWN', '整理办公效率模板。', '示例学院', '信息管理'),
    (4, 5, '肆意妄为', NULL, 'UNKNOWN', '存在多次违规记录的演示账号。', '演示学院', '网络工程'),
    (5, 6, '禁用账号', NULL, 'UNKNOWN', '用于演示管理员禁用和恢复账号。', '演示学院', '软件工程'),
    (6, 7, '锁定账号', NULL, 'UNKNOWN', '用于演示登录锁定状态。', '演示学院', '计算机科学')
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    bio = VALUES(bio),
    school = VALUES(school),
    major = VALUES(major);

INSERT INTO member_point_account (
    id, member_id, level_id, current_points, frozen_points, total_earned_points, total_spent_points
) VALUES
    (2, 2, 2, 180, 0, 240, 60),
    (3, 3, 2, 260, 0, 300, 40),
    (4, 4, 1, 80, 0, 120, 40),
    (5, 5, 1, 30, 0, 30, 0),
    (6, 6, 1, 20, 0, 20, 0)
ON DUPLICATE KEY UPDATE
    level_id = VALUES(level_id),
    current_points = VALUES(current_points),
    frozen_points = VALUES(frozen_points),
    total_earned_points = VALUES(total_earned_points),
    total_spent_points = VALUES(total_spent_points);

INSERT INTO resource_category (
    id, parent_id, category_name, level_no, status, sort_order
) VALUES
    (22, 2, 'PSD素材', 2, 'DISABLED', 2)
ON DUPLICATE KEY UPDATE
    category_name = VALUES(category_name),
    level_no = VALUES(level_no),
    status = VALUES(status),
    sort_order = VALUES(sort_order);

INSERT INTO tag_info (
    id, tag_name, use_count, status
) VALUES
    (8, '剪辑', 2, 'ENABLED'),
    (9, '办公', 3, 'DISABLED'),
    (10, 'UI', 2, 'ENABLED'),
    (11, '版权', 1, 'ENABLED'),
    (12, '违规', 2, 'ENABLED')
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    use_count = VALUES(use_count),
    status = VALUES(status);

INSERT INTO resource_info (
    id, publisher_id, category_id, title, resource_type, summary, description,
    external_url, status, reject_reason, view_count, download_count, favorite_count,
    like_count, comment_count, rating_count, average_rating, current_version_no,
    submitted_time, published_time, offline_time
) VALUES
    (2, 2, 21, 'UI设计全套模板', 'MATERIAL', '移动端和后台页面常用 UI 模板集合。', '包含首页、列表、表单、弹窗等界面模板，适合资源审核演示。', NULL, 'PENDING_REVIEW', NULL, 42, 0, 0, 0, 0, 0, 0.00, 1, NOW(3), NULL, NULL),
    (3, 3, 12, '办公表格合集', 'TEMPLATE', '课程设计常用办公表格和台账模板。', '包含签到表、统计表、项目进度表等内容，适合审核通过和驳回流程演示。', NULL, 'PENDING_REVIEW', NULL, 35, 0, 0, 0, 0, 0, 0.00, 1, NOW(3), NULL, NULL),
    (4, 4, 21, '往期驳回素材', 'MATERIAL', '缺少版权说明的历史素材包。', '该资源保留为已驳回状态，用于演示审核列表中的已处理数据。', NULL, 'REJECTED', '缺少来源说明和版权授权。', 18, 0, 0, 0, 0, 0, 0.00, 1, DATE_SUB(NOW(3), INTERVAL 5 DAY), NULL, NULL),
    (5, 1, 41, '编程入门教程', 'COURSE', 'Java 和 Web 开发入门课程资料。', '包含课件、代码和练习题，用于演示已发布资源的下架、版权下架和删除操作。', NULL, 'PUBLISHED', NULL, 520, 76, 16, 12, 1, 2, 4.50, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 9 DAY), NULL),
    (6, 4, 41, '违规影音文件合集', 'COURSE', '包含不合规说明的影音资料。', '该资源已被管理员下架，用于演示恢复上架流程。', NULL, 'OFFLINE', NULL, 88, 12, 2, 1, 1, 0, 0.00, 1, DATE_SUB(NOW(3), INTERVAL 12 DAY), DATE_SUB(NOW(3), INTERVAL 11 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY)),
    (7, 3, 11, '版权投诉下架课件', 'DOCUMENT', '已因版权投诉处理下架的课件。', '该资源保留版权下架状态，用于演示版权锁定数据。', NULL, 'COPYRIGHT_DOWN', NULL, 110, 23, 4, 2, 0, 0, 0.00, 1, DATE_SUB(NOW(3), INTERVAL 15 DAY), DATE_SUB(NOW(3), INTERVAL 14 DAY), DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    (8, 2, 31, '前端Vue组件源码包', 'SOURCE_CODE', 'Vue 组件和管理端页面示例源码。', '包含若干可复用组件和页面示例，用于版权投诉通过后强制下架演示。', NULL, 'PUBLISHED', NULL, 230, 42, 9, 7, 0, 1, 4.00, 1, DATE_SUB(NOW(3), INTERVAL 7 DAY), DATE_SUB(NOW(3), INTERVAL 6 DAY), NULL)
ON DUPLICATE KEY UPDATE
    publisher_id = VALUES(publisher_id),
    category_id = VALUES(category_id),
    title = VALUES(title),
    resource_type = VALUES(resource_type),
    summary = VALUES(summary),
    description = VALUES(description),
    status = VALUES(status),
    reject_reason = VALUES(reject_reason),
    view_count = VALUES(view_count),
    download_count = VALUES(download_count),
    favorite_count = VALUES(favorite_count),
    like_count = VALUES(like_count),
    comment_count = VALUES(comment_count),
    rating_count = VALUES(rating_count),
    average_rating = VALUES(average_rating),
    submitted_time = VALUES(submitted_time),
    published_time = VALUES(published_time),
    offline_time = VALUES(offline_time),
    deleted_at = NULL;

INSERT INTO resource_version (
    id, resource_id, version_no, title, summary, description, category_id,
    resource_type, external_url, tag_snapshot, attachment_snapshot, submitter_id
) VALUES
    (2, 2, 1, 'UI设计全套模板', '移动端和后台页面常用 UI 模板集合。', '包含首页、列表、表单、弹窗等界面模板，适合资源审核演示。', 21, 'MATERIAL', NULL, JSON_ARRAY('UI', '模板'), JSON_ARRAY(JSON_OBJECT('fileName', 'ui-template-kit.zip', 'fileSize', 4096000)), 2),
    (3, 3, 1, '办公表格合集', '课程设计常用办公表格和台账模板。', '包含签到表、统计表、项目进度表等内容。', 12, 'TEMPLATE', NULL, JSON_ARRAY('办公', '模板'), JSON_ARRAY(JSON_OBJECT('fileName', 'office-sheets.zip', 'fileSize', 1024000)), 3),
    (4, 4, 1, '往期驳回素材', '缺少版权说明的历史素材包。', '该资源保留为已驳回状态。', 21, 'MATERIAL', NULL, JSON_ARRAY('UI'), JSON_ARRAY(JSON_OBJECT('fileName', 'rejected-assets.zip', 'fileSize', 512000)), 4),
    (5, 5, 1, '编程入门教程', 'Java 和 Web 开发入门课程资料。', '包含课件、代码和练习题。', 41, 'COURSE', NULL, JSON_ARRAY('Java', 'SpringBoot'), JSON_ARRAY(JSON_OBJECT('fileName', 'programming-course.zip', 'fileSize', 8192000)), 1),
    (6, 6, 1, '违规影音文件合集', '包含不合规说明的影音资料。', '该资源已被管理员下架。', 41, 'COURSE', NULL, JSON_ARRAY('剪辑', '违规'), JSON_ARRAY(JSON_OBJECT('fileName', 'blocked-media.zip', 'fileSize', 2048000)), 4),
    (7, 7, 1, '版权投诉下架课件', '已因版权投诉处理下架的课件。', '该资源保留版权下架状态。', 11, 'DOCUMENT', NULL, JSON_ARRAY('版权'), JSON_ARRAY(JSON_OBJECT('fileName', 'copyright-courseware.pdf', 'fileSize', 3072000)), 3),
    (8, 8, 1, '前端Vue组件源码包', 'Vue 组件和管理端页面示例源码。', '包含若干可复用组件和页面示例。', 31, 'SOURCE_CODE', NULL, JSON_ARRAY('前端', 'Vue'), JSON_ARRAY(JSON_OBJECT('fileName', 'vue-components.zip', 'fileSize', 5120000)), 2)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    description = VALUES(description),
    tag_snapshot = VALUES(tag_snapshot),
    attachment_snapshot = VALUES(attachment_snapshot);

INSERT INTO resource_tag_rel (resource_id, tag_id)
VALUES
    (2, 10), (2, 7),
    (3, 9), (3, 7),
    (4, 10),
    (5, 4), (5, 5),
    (6, 8), (6, 12),
    (7, 11),
    (8, 6), (8, 7)
ON DUPLICATE KEY UPDATE
    resource_id = VALUES(resource_id);

INSERT INTO file_attachment (
    id, owner_type, owner_id, uploader_id, original_file_name, stored_file_name,
    file_ext, mime_type, file_size, file_hash, storage_path, status, download_count
) VALUES
    (2, 'RESOURCE', 2, 3, 'ui-template-kit.zip', 'resource-2-v1.zip', 'zip', 'application/zip', 4096000, 'demo-file-2', './uploads/resource/2/resource-2-v1.zip', 'NORMAL', 0),
    (3, 'RESOURCE', 3, 4, 'office-sheets.zip', 'resource-3-v1.zip', 'zip', 'application/zip', 1024000, 'demo-file-3', './uploads/resource/3/resource-3-v1.zip', 'NORMAL', 0),
    (4, 'RESOURCE', 4, 5, 'rejected-assets.zip', 'resource-4-v1.zip', 'zip', 'application/zip', 512000, 'demo-file-4', './uploads/resource/4/resource-4-v1.zip', 'NORMAL', 0),
    (5, 'RESOURCE', 5, 1, 'programming-course.zip', 'resource-5-v1.zip', 'zip', 'application/zip', 8192000, 'demo-file-5', './uploads/resource/5/resource-5-v1.zip', 'NORMAL', 76),
    (6, 'RESOURCE', 6, 5, 'blocked-media.zip', 'resource-6-v1.zip', 'zip', 'application/zip', 2048000, 'demo-file-6', './uploads/resource/6/resource-6-v1.zip', 'NORMAL', 12),
    (7, 'RESOURCE', 7, 4, 'copyright-courseware.pdf', 'resource-7-v1.pdf', 'pdf', 'application/pdf', 3072000, 'demo-file-7', './uploads/resource/7/resource-7-v1.pdf', 'NORMAL', 23),
    (8, 'RESOURCE', 8, 3, 'vue-components.zip', 'resource-8-v1.zip', 'zip', 'application/zip', 5120000, 'demo-file-8', './uploads/resource/8/resource-8-v1.zip', 'NORMAL', 42)
ON DUPLICATE KEY UPDATE
    original_file_name = VALUES(original_file_name),
    stored_file_name = VALUES(stored_file_name),
    status = VALUES(status),
    download_count = VALUES(download_count),
    deleted_at = NULL;

INSERT INTO resource_audit_record (
    id, resource_id, version_no, auditor_id, audit_result, reason
) VALUES
    (2, 4, 1, 1, 'REJECTED', '缺少来源说明和版权授权。'),
    (3, 5, 1, 1, 'APPROVED', '演示资源审核通过。'),
    (4, 6, 1, 1, 'OFFLINE', '存在违规影音内容，临时下架。'),
    (5, 7, 1, 1, 'COPYRIGHT_DOWN', '版权投诉成立，强制下架。'),
    (6, 8, 1, 1, 'APPROVED', '源码资源审核通过。')
ON DUPLICATE KEY UPDATE
    audit_result = VALUES(audit_result),
    reason = VALUES(reason);

INSERT INTO resource_status_log (
    id, resource_id, from_status, to_status, operator_id, reason
) VALUES
    (2, 2, 'DRAFT', 'PENDING_REVIEW', 3, '用户提交审核。'),
    (3, 3, 'DRAFT', 'PENDING_REVIEW', 4, '用户提交审核。'),
    (4, 4, 'PENDING_REVIEW', 'REJECTED', 2, '缺少来源说明和版权授权。'),
    (5, 5, 'PENDING_REVIEW', 'PUBLISHED', 2, '演示资源审核通过。'),
    (6, 6, 'PUBLISHED', 'OFFLINE', 2, '存在违规影音内容，临时下架。'),
    (7, 7, 'PUBLISHED', 'COPYRIGHT_DOWN', 2, '版权投诉成立，强制下架。'),
    (8, 8, 'PENDING_REVIEW', 'PUBLISHED', 2, '源码资源审核通过。')
ON DUPLICATE KEY UPDATE
    from_status = VALUES(from_status),
    to_status = VALUES(to_status),
    reason = VALUES(reason);

INSERT INTO comment_info (
    id, target_type, target_id, member_id, parent_id, root_id, to_member_id, content, status, deleted_at
) VALUES
    (1, 'RESOURCE', 1, 4, NULL, NULL, NULL, '恶意辱骂违规言论', 'ACTIVE', NULL),
    (2, 'RESOURCE', 5, 3, NULL, NULL, NULL, '已删除历史评论', 'DELETED', NOW(3)),
    (3, 'RESOURCE', 6, 4, NULL, NULL, NULL, '广告引流灌水内容', 'ACTIVE', NULL),
    (4, 'RESOURCE', 1, 5, NULL, NULL, NULL, '管理员已隐藏的争议评论', 'HIDDEN', NULL),
    (5, 'REQUEST_POST', 1, 2, NULL, NULL, NULL, '我也需要这个模板，最好带接口文档。', 'ACTIVE', NULL)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    status = VALUES(status),
    deleted_at = VALUES(deleted_at);

INSERT INTO request_post (
    id, requester_id, category_id, title, content, expected_format,
    reward_points, status, accepted_reply_id, view_count, answer_count, comment_count, resolved_time, closed_time
) VALUES
    (2, 4, 31, '违规资源求购帖', '这里保留一个已经被管理员关闭的求资源帖子，用于后台管理端展示关闭状态和举报处理联动。', 'zip 或链接', 0, 'CLOSED', NULL, 68, 0, 0, NULL, DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    (3, 2, 12, '闲置书籍互换求助', '希望寻找几本计算机网络和数据库相关的课程书籍，可以接受电子版整理资料。', 'pdf 或图片', 0, 'ONGOING', NULL, 25, 0, 0, NULL, NULL),
    (4, 3, 31, '前端组件库求助', '需要一套适合课程设计的 Vue 组件库示例，最好包含表格、弹窗和表单校验。', '源码压缩包', 20, 'RESOLVED', 2, 90, 1, 0, DATE_SUB(NOW(3), INTERVAL 2 DAY), NULL)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    reward_points = VALUES(reward_points),
    status = VALUES(status),
    accepted_reply_id = VALUES(accepted_reply_id),
    view_count = VALUES(view_count),
    answer_count = VALUES(answer_count),
    comment_count = VALUES(comment_count),
    resolved_time = VALUES(resolved_time),
    closed_time = VALUES(closed_time),
    deleted_at = NULL;

INSERT INTO request_reply (
    id, request_id, replier_id, content, resource_id, external_url, status, is_accepted, accepted_time, deleted_at
) VALUES
    (1, 1, 2, '我整理了一份 Spring Boot 后端模板，可以参考资源 8 的源码结构。', 8, NULL, 'ACTIVE', 0, NULL, NULL),
    (2, 4, 1, '这里有一份 Vue 组件源码包，已经上传到资源区。', 8, NULL, 'ACTIVE', 1, DATE_SUB(NOW(3), INTERVAL 2 DAY), NULL),
    (3, 2, 4, '违规回复内容，保留给后台删除回复演示。', NULL, NULL, 'ACTIVE', 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    resource_id = VALUES(resource_id),
    status = VALUES(status),
    is_accepted = VALUES(is_accepted),
    accepted_time = VALUES(accepted_time),
    deleted_at = VALUES(deleted_at);

INSERT INTO request_tag_rel (request_id, tag_id)
VALUES
    (2, 12),
    (3, 9),
    (4, 6),
    (4, 7)
ON DUPLICATE KEY UPDATE
    request_id = VALUES(request_id);

INSERT INTO request_status_log (
    id, request_id, from_status, to_status, operator_id, reason
) VALUES
    (2, 2, 'ONGOING', 'CLOSED', 2, '违规资源求购，管理员关闭。'),
    (3, 3, NULL, 'ONGOING', 3, '演示求资源帖创建。'),
    (4, 4, 'ONGOING', 'RESOLVED', 4, '采纳回复并结算。')
ON DUPLICATE KEY UPDATE
    from_status = VALUES(from_status),
    to_status = VALUES(to_status),
    reason = VALUES(reason);

INSERT INTO report_complaint (
    id, reporter_id, report_type, target_type, target_id, title, reason,
    proof_summary, contact_email, status, handler_id, handle_result, handle_time
) VALUES
    (1, 2, 'COMMENT', 'COMMENT', 1, '举报违规评论', '评论存在辱骂和攻击性表达。', '评论截图已上传。', 'reporter1@example.com', 'PENDING', NULL, NULL, NULL),
    (2, 3, 'RESOURCE', 'RESOURCE', 5, '举报违规资源', '资源描述和附件可能包含不合规内容。', '已记录资源页面链接。', 'reporter2@example.com', 'PENDING', NULL, NULL, NULL),
    (3, 1, 'REQUEST_POST', 'REQUEST_POST', 2, '举报违规求资源帖', '帖子引导交换违规资源。', '已截图留存。', 'demo@example.com', 'RESOLVED', 1, '已核实并关闭帖子。', DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    (4, 4, 'REQUEST_REPLY', 'REQUEST_REPLY', 3, '举报违规回复', '回复内容存在引流信息。', '回复截图已上传。', 'user003@example.com', 'PENDING', NULL, NULL, NULL),
    (5, 2, 'COPYRIGHT', 'RESOURCE', 8, '版权投诉：Vue组件源码包', '该资源疑似未经授权转载。', '版权登记截图和原始链接。', 'copyright-a@example.com', 'PENDING', NULL, NULL, NULL),
    (6, 3, 'COPYRIGHT', 'RESOURCE', 7, '版权投诉：历史课件', '投诉材料不足。', '仅提供口头描述。', 'copyright-b@example.com', 'REJECTED', 1, '材料不足，驳回投诉。', DATE_SUB(NOW(3), INTERVAL 2 DAY)),
    (7, 1, 'COPYRIGHT', 'RESOURCE', 7, '版权投诉：课件侵权', '版权方补充材料后投诉成立。', '授权证明不一致。', 'copyright-c@example.com', 'RESOLVED', 1, '投诉成立，资源版权下架。', DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    (8, 2, 'USER', 'USER', 4, '举报违规用户', '该用户多次发布违规资源。', '账号历史记录。', 'reporter1@example.com', 'PENDING', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    reporter_id = VALUES(reporter_id),
    report_type = VALUES(report_type),
    target_type = VALUES(target_type),
    target_id = VALUES(target_id),
    title = VALUES(title),
    reason = VALUES(reason),
    proof_summary = VALUES(proof_summary),
    contact_email = VALUES(contact_email),
    status = VALUES(status),
    handler_id = VALUES(handler_id),
    handle_result = VALUES(handle_result),
    handle_time = VALUES(handle_time),
    deleted_at = NULL;

INSERT INTO appeal_record (
    id, appellant_id, target_type, target_id, related_report_id, reason,
    status, handler_id, handle_result, handle_time
) VALUES
    (1, 4, 'RESOURCE', 6, 2, '资源已完成整改，请求恢复上架。', 'PENDING', NULL, NULL, NULL),
    (2, 3, 'RESOURCE', 7, 6, '认为版权投诉材料不足，请求复核。', 'REJECTED', 1, '复核后仍维持版权下架。', DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    (3, 1, 'REQUEST_POST', 2, 3, '帖子内容已修改，希望恢复展示。', 'APPROVED', 1, '申诉通过，已记录处理结果。', DATE_SUB(NOW(3), INTERVAL 1 DAY))
ON DUPLICATE KEY UPDATE
    appellant_id = VALUES(appellant_id),
    target_type = VALUES(target_type),
    target_id = VALUES(target_id),
    related_report_id = VALUES(related_report_id),
    reason = VALUES(reason),
    status = VALUES(status),
    handler_id = VALUES(handler_id),
    handle_result = VALUES(handle_result),
    handle_time = VALUES(handle_time),
    deleted_at = NULL;

INSERT INTO notification_event (
    id, event_type, source_type, source_id, receiver_id, payload, status, process_time
) VALUES
    (2, 'MEMBER_STATUS', 'MEMBER', 5, 5, JSON_OBJECT('title', '账号状态通知', 'content', '你的账号已被管理员禁用。'), 'SENT', NOW(3)),
    (3, 'REPORT_RESULT', 'REPORT_COMPLAINT', 3, 1, JSON_OBJECT('title', '举报处理结果', 'content', '你的举报已处理。'), 'SENT', NOW(3)),
    (4, 'APPEAL_RESULT', 'APPEAL', 2, 3, JSON_OBJECT('title', '申诉处理结果', 'content', '你的申诉已驳回。'), 'SENT', NOW(3))
ON DUPLICATE KEY UPDATE
    payload = VALUES(payload),
    status = VALUES(status),
    process_time = VALUES(process_time);

INSERT INTO system_notice (
    id, event_id, receiver_id, notice_type, title, content, target_type, target_id, is_read
) VALUES
    (2, 2, 5, 'MEMBER_STATUS', '账号状态通知', '你的账号已被管理员禁用，如有疑问可提交申诉。', 'MEMBER', 5, 0),
    (3, 3, 1, 'REPORT_RESULT', '举报处理结果', '你提交的违规求资源帖举报已处理。', 'REQUEST_POST', 2, 0),
    (4, 4, 3, 'APPEAL_RESULT', '申诉处理结果', '你的版权下架申诉已驳回。', 'RESOURCE', 7, 1)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    is_read = VALUES(is_read),
    deleted_at = NULL;

INSERT INTO system_config (
    id, config_key, config_value, value_type, description, is_sensitive, is_enabled
) VALUES
    (1, 'upload.allowed_types', 'pdf,doc,docx,ppt,pptx,xls,xlsx,zip,rar,7z,png,jpg,jpeg,txt,md', 'STRING', '允许上传文件类型', 0, 1),
    (2, 'upload.max_file_size_mb', '100', 'INTEGER', '单文件最大上传大小（MB）', 0, 1),
    (3, 'resource.max_files_per_resource', '5', 'INTEGER', '单资源最大附件数', 0, 1),
    (4, 'auth.max_failed_login_count', '5', 'INTEGER', '连续登录失败锁定次数', 0, 1),
    (5, 'request.default_reward_limit', '100', 'INTEGER', '普通会员悬赏积分上限', 0, 1),
    (6, 'point.resource_approved', '10', 'INTEGER', '资源审核通过 + 积分', 0, 1),
    (7, 'point.resource_downloaded', '2', 'INTEGER', '资源被下载 + 积分/次', 0, 1),
    (8, 'point.request_accepted', '5', 'INTEGER', '求资源回答被采纳 + 奖励积分', 0, 1),
    (9, 'point.resource_offline_penalty', '20', 'INTEGER', '资源违规下架 - 扣积分', 0, 1),
    (10, 'point.comment_delete_penalty', '5', 'INTEGER', '评论违规删除 - 扣积分', 0, 1),
    (11, 'resource.daily_publish_limit', '5', 'INTEGER', '用户每日最大发布资源数', 0, 1),
    (12, 'auth.email_code_minutes', '10', 'INTEGER', '邮箱验证码有效期（分钟）', 0, 1)
ON DUPLICATE KEY UPDATE
    config_key = VALUES(config_key),
    config_value = VALUES(config_value),
    value_type = VALUES(value_type),
    description = VALUES(description),
    is_sensitive = VALUES(is_sensitive),
    is_enabled = VALUES(is_enabled);

INSERT INTO admin_operation_log (
    id, admin_id, operation_type, target_type, target_id, content,
    before_snapshot, after_snapshot, ip
) VALUES
    (1, 1, 'ACCOUNT_LOGIN', 'ADMIN', 1, '管理员登录后台', JSON_OBJECT('status', '未登录'), JSON_OBJECT('status', '正常登录后台'), '192.168.1.101'),
    (2, 1, 'RESOURCE_APPROVE', 'RESOURCE', 5, '资源审核通过', JSON_OBJECT('status', 'PENDING_REVIEW'), JSON_OBJECT('status', 'PUBLISHED'), '192.168.1.102'),
    (3, 1, 'RESOURCE_OFFLINE', 'RESOURCE', 6, '资源违规下架', JSON_OBJECT('status', 'PUBLISHED'), JSON_OBJECT('status', 'OFFLINE'), '192.168.1.101'),
    (4, 1, 'RESOURCE_RESTORE', 'RESOURCE', 6, '资源恢复上架', JSON_OBJECT('status', 'OFFLINE'), JSON_OBJECT('status', 'PUBLISHED'), '192.168.1.103'),
    (5, 1, 'MEMBER_DISABLED', 'MEMBER', 5, '用户账号禁用', JSON_OBJECT('status', 'NORMAL'), JSON_OBJECT('status', 'DISABLED'), '192.168.1.102'),
    (6, 1, 'MEMBER_NORMAL', 'MEMBER', 5, '用户账号恢复', JSON_OBJECT('status', 'DISABLED'), JSON_OBJECT('status', 'NORMAL'), '192.168.1.101'),
    (7, 1, 'COMMENT_DELETED', 'COMMENT', 2, '删除违规评论', JSON_OBJECT('status', 'ACTIVE'), JSON_OBJECT('status', 'DELETED'), '192.168.1.104'),
    (8, 1, 'COMMENT_ACTIVE', 'COMMENT', 2, '恢复合法评论', JSON_OBJECT('status', 'DELETED'), JSON_OBJECT('status', 'ACTIVE'), '192.168.1.103'),
    (9, 1, 'REPORT_HANDLE', 'REPORT_COMPLAINT', 3, '举报处理', JSON_OBJECT('status', 'PENDING'), JSON_OBJECT('status', 'RESOLVED'), '192.168.1.102'),
    (10, 1, 'APPEAL_HANDLE', 'APPEAL', 2, '申诉处理', JSON_OBJECT('status', 'PENDING'), JSON_OBJECT('status', 'REJECTED'), '192.168.1.101'),
    (11, 1, 'CATEGORY_CREATE', 'CATEGORY', 22, '分类新增', JSON_OBJECT('status', 'NONE'), JSON_OBJECT('status', 'ENABLED'), '192.168.1.104'),
    (12, 1, 'TAG_UPDATE', 'TAG', 9, '标签修改', JSON_OBJECT('status', 'ENABLED'), JSON_OBJECT('status', 'DISABLED'), '192.168.1.103'),
    (13, 1, 'MEMBER_LEVEL_UPDATE', 'MEMBERSHIP_LEVEL', 3, '等级权益配置', JSON_OBJECT('status', 'OLD'), JSON_OBJECT('status', 'UPDATED'), '192.168.1.102')
ON DUPLICATE KEY UPDATE
    operation_type = VALUES(operation_type),
    target_type = VALUES(target_type),
    target_id = VALUES(target_id),
    content = VALUES(content),
    before_snapshot = VALUES(before_snapshot),
    after_snapshot = VALUES(after_snapshot),
    ip = VALUES(ip),
    deleted_at = NULL;
