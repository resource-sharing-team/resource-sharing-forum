-- User frontend demo data aligned with web_user mock records.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO resource_category (
    id, parent_id, category_name, level_no, status, sort_order
) VALUES
    (13, 1, '学习笔记', 2, 'ENABLED', 3),
    (23, 2, '字体图标', 2, 'ENABLED', 3),
    (33, 3, '完整项目', 2, 'ENABLED', 3),
    (42, 4, '办公教程', 2, 'ENABLED', 2),
    (43, 4, '设计教程', 2, 'ENABLED', 3),
    (52, 5, '设计工具', 2, 'ENABLED', 2),
    (53, 5, '效率工具', 2, 'ENABLED', 3)
ON DUPLICATE KEY UPDATE
    category_name = VALUES(category_name),
    level_no = VALUES(level_no),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    deleted_at = NULL;

INSERT INTO user_account (
    id, username, email, password_hash, role, status, failed_login_count, locked_until
) VALUES
    (8, 'design_master', 'design-master@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (9, 'data_analyst', 'data-analyst@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (10, 'resume_helper', 'resume-helper@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (11, 'frontend_bird', 'frontend-bird@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (12, 'photo_akai', 'photo-akai@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (13, 'teacher_exam', 'teacher-exam@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (14, 'data_learner', 'data-learner@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (15, 'ui_designer', 'ui-designer@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL),
    (16, 'java_advanced', 'java-advanced@example.com', '$2a$10$J2N3JOui3Jy3/dqeh3zHFOL9OPovGg9AVXAz2HbhfNz.a7XuMu5se', 'USER', 'NORMAL', 0, NULL)
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    status = VALUES(status),
    failed_login_count = VALUES(failed_login_count),
    locked_until = VALUES(locked_until),
    deleted_at = NULL;

INSERT INTO member_profile (
    id, account_id, nickname, avatar_url, gender, bio, school, major
) VALUES
    (7, 8, '设计大师', NULL, 'UNKNOWN', '长期整理 UI 模板和设计规范素材。', '示例大学', '视觉传达设计'),
    (8, 9, '数据分析师', NULL, 'UNKNOWN', '分享 Python 数据分析资料和实战数据集。', '示例大学', '数据科学'),
    (9, 10, '求职助手', NULL, 'UNKNOWN', '收集简历模板、面试资料和求职工具。', '示例学院', '人力资源管理'),
    (10, 11, '前端老鸟', NULL, 'UNKNOWN', '整理前端源码、组件库和后台模板。', '示例大学', '软件工程'),
    (11, 12, '摄影师阿凯', NULL, 'UNKNOWN', '分享高清无版权摄影图集。', '示例学院', '摄影'),
    (12, 13, '教资考生', NULL, 'UNKNOWN', '备考教师资格证。', '示例大学', '教育学'),
    (13, 14, '数据学习者', NULL, 'UNKNOWN', '正在学习 Python 和数据分析。', '示例大学', '统计学'),
    (14, 15, 'UI设计师', NULL, 'UNKNOWN', '关注 B 端产品和设计系统。', '示例大学', '工业设计'),
    (15, 16, 'Java进阶', NULL, 'UNKNOWN', '学习 Java 微服务和云原生部署。', '示例大学', '计算机科学')
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    bio = VALUES(bio),
    school = VALUES(school),
    major = VALUES(major),
    deleted_at = NULL;

INSERT INTO member_point_account (
    id, member_id, level_id, current_points, frozen_points, total_earned_points, total_spent_points
) VALUES
    (7, 7, 3, 960, 0, 1200, 240),
    (8, 8, 3, 880, 0, 940, 60),
    (9, 9, 2, 460, 0, 500, 40),
    (10, 10, 3, 780, 0, 900, 120),
    (11, 11, 2, 320, 0, 360, 40),
    (12, 12, 1, 80, 0, 80, 0),
    (13, 13, 1, 120, 0, 120, 0),
    (14, 14, 2, 260, 0, 300, 40),
    (15, 15, 2, 340, 0, 360, 20)
ON DUPLICATE KEY UPDATE
    level_id = VALUES(level_id),
    current_points = VALUES(current_points),
    frozen_points = VALUES(frozen_points),
    total_earned_points = VALUES(total_earned_points),
    total_spent_points = VALUES(total_spent_points),
    deleted_at = NULL;

INSERT INTO tag_info (
    id, tag_name, use_count, status
) VALUES
    (20, 'Python', 2, 'ENABLED'),
    (21, '数据分析', 2, 'ENABLED'),
    (22, '源码', 2, 'ENABLED'),
    (23, '简历', 1, 'ENABLED'),
    (24, 'Word', 1, 'ENABLED'),
    (25, '求职', 1, 'ENABLED'),
    (26, 'Vue3', 1, 'ENABLED'),
    (27, '后台管理', 1, 'ENABLED'),
    (28, '风景', 1, 'ENABLED'),
    (29, '高清', 1, 'ENABLED'),
    (30, '无版权', 1, 'ENABLED'),
    (31, 'Figma', 1, 'ENABLED'),
    (32, '设计模板', 1, 'ENABLED'),
    (33, '教资', 1, 'ENABLED'),
    (34, '面试', 1, 'ENABLED'),
    (35, '微服务', 1, 'ENABLED')
ON DUPLICATE KEY UPDATE
    use_count = VALUES(use_count),
    status = VALUES(status),
    deleted_at = NULL;

UPDATE resource_info
SET title = '2026 考研政治历年真题完整版',
    summary = '包含 2010-2025 年考研政治真题及解析，PDF 高清无水印，可直接打印。',
    description = '资料按年份整理，附带答案解析、错题索引和重点知识点标注。适合冲刺阶段快速回顾，也适合基础阶段按章节补漏。',
    download_count = 136,
    rating_count = 86,
    average_rating = 4.80
WHERE id = 1;

INSERT INTO resource_info (
    id, publisher_id, category_id, title, resource_type, summary, description,
    external_url, status, view_count, download_count, favorite_count, like_count,
    comment_count, rating_count, average_rating, current_version_no, submitted_time, published_time
) VALUES
    (9, 7, 21, 'UI 设计全套 Figma 模板合集', 'MATERIAL', '包含移动端、网页后台、组件库，适合课程作业、毕设和快速出稿。', '模板采用自动布局和组件化命名，含色板、字体规范、按钮、表单、后台表格、移动端业务流程等页面。', NULL, 'PUBLISHED', 910, 249, 31, 18, 0, 113, 4.90, 1, DATE_SUB(NOW(3), INTERVAL 13 DAY), DATE_SUB(NOW(3), INTERVAL 12 DAY)),
    (10, 8, 41, 'Python 数据分析入门到实战教程', 'COURSE', '从 Python 基础到数据清洗、可视化、建模案例，配套源码与数据集。', '每章提供 notebook、案例数据和练习题，覆盖 Pandas、Matplotlib、Seaborn、Scikit-learn 入门。', NULL, 'PUBLISHED', 760, 312, 24, 15, 0, 74, 4.70, 1, DATE_SUB(NOW(3), INTERVAL 12 DAY), DATE_SUB(NOW(3), INTERVAL 11 DAY)),
    (11, 9, 12, '极简个人简历模板合集 100 套', 'TEMPLATE', 'Word 可编辑简历模板，实习、校招、转岗通用，替换内容即可使用。', '包含中文简历、英文简历、单页简历、作品集封面等多类模板，附简历撰写建议。', NULL, 'PUBLISHED', 1320, 568, 66, 40, 0, 121, 4.90, 1, DATE_SUB(NOW(3), INTERVAL 11 DAY), DATE_SUB(NOW(3), INTERVAL 10 DAY)),
    (12, 10, 31, 'Vue3 后台管理系统模板', 'SOURCE_CODE', '包含登录、权限、动态路由、菜单管理和常用业务组件。', '项目基于 Vue3、TypeScript、Element Plus，适合后台管理类课程设计和中小型项目快速启动。', NULL, 'PUBLISHED', 980, 421, 42, 25, 0, 67, 4.80, 1, DATE_SUB(NOW(3), INTERVAL 10 DAY), DATE_SUB(NOW(3), INTERVAL 9 DAY)),
    (13, 11, 22, '高清风景摄影图集', 'MATERIAL', '4K 高清风景图片，无版权可商用，适合设计素材和演示文档。', '图片按山川、湖泊、城市、日落四类整理，均附尺寸说明和使用许可备注。', NULL, 'PUBLISHED', 870, 412, 50, 28, 0, 92, 4.90, 1, DATE_SUB(NOW(3), INTERVAL 9 DAY), DATE_SUB(NOW(3), INTERVAL 8 DAY))
ON DUPLICATE KEY UPDATE
    publisher_id = VALUES(publisher_id),
    category_id = VALUES(category_id),
    title = VALUES(title),
    resource_type = VALUES(resource_type),
    summary = VALUES(summary),
    description = VALUES(description),
    status = VALUES(status),
    view_count = VALUES(view_count),
    download_count = VALUES(download_count),
    favorite_count = VALUES(favorite_count),
    like_count = VALUES(like_count),
    rating_count = VALUES(rating_count),
    average_rating = VALUES(average_rating),
    submitted_time = VALUES(submitted_time),
    published_time = VALUES(published_time),
    deleted_at = NULL;

INSERT INTO resource_version (
    id, resource_id, version_no, title, summary, description, category_id,
    resource_type, external_url, tag_snapshot, attachment_snapshot, submitter_id
) VALUES
    (9, 9, 1, 'UI 设计全套 Figma 模板合集', '包含移动端、网页后台、组件库，适合课程作业、毕设和快速出稿。', '模板采用自动布局和组件化命名，含色板、字体规范、按钮、表单、后台表格、移动端业务流程等页面。', 21, 'MATERIAL', NULL, JSON_ARRAY('UI', 'Figma', '设计模板'), JSON_ARRAY(JSON_OBJECT('fileName', 'figma-ui-kit.zip', 'fileSize', 113246208)), 7),
    (10, 10, 1, 'Python 数据分析入门到实战教程', '从 Python 基础到数据清洗、可视化、建模案例，配套源码与数据集。', '每章提供 notebook、案例数据和练习题，覆盖 Pandas、Matplotlib、Seaborn、Scikit-learn 入门。', 41, 'COURSE', NULL, JSON_ARRAY('Python', '数据分析', '源码'), JSON_ARRAY(JSON_OBJECT('fileName', 'python-data-analysis.zip', 'fileSize', 90177536)), 8),
    (11, 11, 1, '极简个人简历模板合集 100 套', 'Word 可编辑简历模板，实习、校招、转岗通用，替换内容即可使用。', '包含中文简历、英文简历、单页简历、作品集封面等多类模板，附简历撰写建议。', 12, 'TEMPLATE', NULL, JSON_ARRAY('简历', 'Word', '求职'), JSON_ARRAY(JSON_OBJECT('fileName', 'resume-templates.zip', 'fileSize', 56623104)), 9),
    (12, 12, 1, 'Vue3 后台管理系统模板', '包含登录、权限、动态路由、菜单管理和常用业务组件。', '项目基于 Vue3、TypeScript、Element Plus，适合后台管理类课程设计和中小型项目快速启动。', 31, 'SOURCE_CODE', NULL, JSON_ARRAY('Vue3', '后台管理', '前端'), JSON_ARRAY(JSON_OBJECT('fileName', 'vue3-admin-template.zip', 'fileSize', 19922944)), 10),
    (13, 13, 1, '高清风景摄影图集', '4K 高清风景图片，无版权可商用，适合设计素材和演示文档。', '图片按山川、湖泊、城市、日落四类整理，均附尺寸说明和使用许可备注。', 22, 'MATERIAL', NULL, JSON_ARRAY('风景', '高清', '无版权'), JSON_ARRAY(JSON_OBJECT('fileName', 'landscape-4k.zip', 'fileSize', 241172480)), 11)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    description = VALUES(description),
    tag_snapshot = VALUES(tag_snapshot),
    attachment_snapshot = VALUES(attachment_snapshot);

INSERT INTO resource_tag_rel (resource_id, tag_id)
VALUES
    (9, 10), (9, 31), (9, 32),
    (10, 20), (10, 21), (10, 22),
    (11, 23), (11, 24), (11, 25),
    (12, 26), (12, 27), (12, 6),
    (13, 28), (13, 29), (13, 30)
ON DUPLICATE KEY UPDATE
    resource_id = VALUES(resource_id);

INSERT INTO file_attachment (
    id, owner_type, owner_id, uploader_id, original_file_name, stored_file_name,
    file_ext, mime_type, file_size, file_hash, storage_path, status, download_count
) VALUES
    (14, 'RESOURCE', 1, 1, '2021-2025 真题与解析.pdf', 'resource-1-analysis.pdf', 'pdf', 'application/pdf', 13316915, 'demo-file-14', './uploads/resource/1/resource-1-analysis.pdf', 'NORMAL', 42),
    (15, 'RESOURCE', 1, 1, '政治高频考点速查表.xlsx', 'resource-1-points.xlsx', 'xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 1677721, 'demo-file-15', './uploads/resource/1/resource-1-points.xlsx', 'NORMAL', 18),
    (16, 'RESOURCE', 9, 8, '移动端页面模板.fig', 'resource-9-mobile.fig', 'fig', 'application/octet-stream', 44040192, 'demo-file-16', './uploads/resource/9/resource-9-mobile.fig', 'NORMAL', 88),
    (17, 'RESOURCE', 9, 8, '后台管理组件库.fig', 'resource-9-admin.fig', 'fig', 'application/octet-stream', 60817408, 'demo-file-17', './uploads/resource/9/resource-9-admin.fig', 'NORMAL', 112),
    (18, 'RESOURCE', 9, 8, '设计规范说明.pdf', 'resource-9-spec.pdf', 'pdf', 'application/pdf', 8388608, 'demo-file-18', './uploads/resource/9/resource-9-spec.pdf', 'NORMAL', 49),
    (19, 'RESOURCE', 10, 9, '课程讲义.pdf', 'resource-10-slides.pdf', 'pdf', 'application/pdf', 16777216, 'demo-file-19', './uploads/resource/10/resource-10-slides.pdf', 'NORMAL', 96),
    (20, 'RESOURCE', 10, 9, 'notebook 源码.zip', 'resource-10-notebook.zip', 'zip', 'application/zip', 24117248, 'demo-file-20', './uploads/resource/10/resource-10-notebook.zip', 'NORMAL', 122),
    (21, 'RESOURCE', 10, 9, '练习数据集.zip', 'resource-10-dataset.zip', 'zip', 'application/zip', 49283072, 'demo-file-21', './uploads/resource/10/resource-10-dataset.zip', 'NORMAL', 94),
    (22, 'RESOURCE', 11, 10, '中文简历模板.zip', 'resource-11-cn.zip', 'zip', 'application/zip', 29360128, 'demo-file-22', './uploads/resource/11/resource-11-cn.zip', 'NORMAL', 260),
    (23, 'RESOURCE', 11, 10, '英文简历模板.zip', 'resource-11-en.zip', 'zip', 'application/zip', 18874368, 'demo-file-23', './uploads/resource/11/resource-11-en.zip', 'NORMAL', 188),
    (24, 'RESOURCE', 11, 10, '简历写作指南.pdf', 'resource-11-guide.pdf', 'pdf', 'application/pdf', 8388608, 'demo-file-24', './uploads/resource/11/resource-11-guide.pdf', 'NORMAL', 120),
    (25, 'RESOURCE', 12, 11, '项目源码.zip', 'resource-12-source.zip', 'zip', 'application/zip', 14680064, 'demo-file-25', './uploads/resource/12/resource-12-source.zip', 'NORMAL', 310),
    (26, 'RESOURCE', 12, 11, '接口文档.md', 'resource-12-api.md', 'md', 'text/markdown', 327680, 'demo-file-26', './uploads/resource/12/resource-12-api.md', 'NORMAL', 56),
    (27, 'RESOURCE', 12, 11, '部署说明.pdf', 'resource-12-deploy.pdf', 'pdf', 'application/pdf', 4928307, 'demo-file-27', './uploads/resource/12/resource-12-deploy.pdf', 'NORMAL', 55),
    (28, 'RESOURCE', 13, 12, '山川湖泊 4K.zip', 'resource-13-nature.zip', 'zip', 'application/zip', 100663296, 'demo-file-28', './uploads/resource/13/resource-13-nature.zip', 'NORMAL', 170),
    (29, 'RESOURCE', 13, 12, '城市日落 4K.zip', 'resource-13-city.zip', 'zip', 'application/zip', 134217728, 'demo-file-29', './uploads/resource/13/resource-13-city.zip', 'NORMAL', 210),
    (30, 'RESOURCE', 13, 12, '授权说明.txt', 'resource-13-license.txt', 'txt', 'text/plain', 24576, 'demo-file-30', './uploads/resource/13/resource-13-license.txt', 'NORMAL', 32)
ON DUPLICATE KEY UPDATE
    original_file_name = VALUES(original_file_name),
    stored_file_name = VALUES(stored_file_name),
    file_size = VALUES(file_size),
    status = VALUES(status),
    download_count = VALUES(download_count),
    deleted_at = NULL;

INSERT INTO resource_rating (
    id, member_id, resource_id, score, comment
) VALUES
    (20, 1, 9, 4, 'Figma 模板质量较高。'),
    (21, 1, 11, 5, '简历模板很实用。')
ON DUPLICATE KEY UPDATE
    score = VALUES(score),
    comment = VALUES(comment),
    deleted_at = NULL;

INSERT INTO user_interaction (
    id, member_id, target_type, target_id, action_type, status
) VALUES
    (1, 1, 'RESOURCE', 1, 'LIKE', 'ACTIVE'),
    (2, 1, 'RESOURCE', 9, 'FAVORITE', 'ACTIVE'),
    (3, 1, 'RESOURCE', 11, 'LIKE', 'ACTIVE'),
    (4, 1, 'RESOURCE', 11, 'FAVORITE', 'ACTIVE'),
    (5, 1, 'RESOURCE', 13, 'FAVORITE', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    deleted_at = NULL;

INSERT INTO request_post (
    id, requester_id, category_id, title, content, expected_format,
    reward_points, status, accepted_reply_id, view_count, answer_count, comment_count, resolved_time, closed_time
) VALUES
    (10, 12, 11, '求 2026 教资面试结构化真题及解析', '急需教师资格证面试结构化真题与答题模板，最好是 PDF 版本，有重点标注，方便冲刺复习。', 'PDF / Word', 0, 'ONGOING', NULL, 140, 3, 1, NULL, NULL),
    (11, 13, 41, '求 Python 数据分析实战项目源码', '想要 2-3 个可直接运行的数据分析项目，最好带数据集和说明，适合作为课程设计参考。', '源码 / Notebook', 100, 'RESOLVED', 20, 220, 5, 1, DATE_SUB(NOW(3), INTERVAL 1 DAY), NULL),
    (12, 14, 21, '求 B 端产品 UI 设计规范文档', '需要颜色、字体、间距、组件规范和表格页面规范，PDF 或 Figma 均可，希望能直接参考。', 'PDF / Figma', 30, 'ONGOING', NULL, 95, 2, 1, NULL, NULL),
    (13, 15, 41, '求 Java 微服务项目实战教程', '需要 Spring Cloud、Docker、K8s 微服务实战教程，包含源码和部署文档，最好能本地跑通。', '视频 / 源码', 500, 'ONGOING', NULL, 160, 1, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    expected_format = VALUES(expected_format),
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
    (20, 11, 8, '可以参考我发布的 Python 数据分析教程，里面有 notebook、源码和练习数据集。', 10, NULL, 'ACTIVE', 1, DATE_SUB(NOW(3), INTERVAL 1 DAY), NULL),
    (21, 10, 1, '我有一份整理版，包含结构化问答和试讲模板。', NULL, NULL, 'ACTIVE', 0, NULL, NULL),
    (22, 10, 14, '可以补充一个面试流程清单。', NULL, NULL, 'ACTIVE', 0, NULL, NULL),
    (23, 12, 7, '可以先看 Figma 模板包里的设计规范说明。', 9, NULL, 'ACTIVE', 0, NULL, NULL),
    (24, 13, 10, '有一套 Spring Cloud 项目结构说明，稍后整理上传。', NULL, NULL, 'ACTIVE', 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    resource_id = VALUES(resource_id),
    status = VALUES(status),
    is_accepted = VALUES(is_accepted),
    accepted_time = VALUES(accepted_time),
    deleted_at = VALUES(deleted_at);

INSERT INTO request_tag_rel (request_id, tag_id)
VALUES
    (10, 33), (10, 34),
    (11, 20), (11, 21),
    (12, 10), (12, 32),
    (13, 4), (13, 35)
ON DUPLICATE KEY UPDATE
    request_id = VALUES(request_id);

INSERT INTO comment_info (
    id, target_type, target_id, member_id, parent_id, root_id, to_member_id, content, status, deleted_at
) VALUES
    (20, 'RESOURCE', 1, 3, NULL, NULL, NULL, '文件可以正常打开，年份目录也很清楚。', 'ACTIVE', NULL),
    (21, 'RESOURCE', 1, 1, NULL, NULL, NULL, '如果大家发现缺页，可以在评论区标注年份，我会更新附件。', 'ACTIVE', NULL),
    (22, 'REQUEST_POST', 10, 2, NULL, NULL, NULL, '我有一份整理版，包含结构化问答和试讲模板。', 'ACTIVE', NULL),
    (23, 'REQUEST_POST', 10, 14, NULL, NULL, NULL, '可以补充一个面试流程清单。', 'ACTIVE', NULL),
    (24, 'REQUEST_POST', 11, 8, NULL, NULL, NULL, '可以参考资源区的数据分析教程。', 'ACTIVE', NULL)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    status = VALUES(status),
    deleted_at = VALUES(deleted_at);

INSERT INTO notification_event (
    id, event_type, source_type, source_id, receiver_id, payload, status, process_time
) VALUES
    (10, 'REQUEST_REPLY', 'REQUEST_POST', 1, 1, JSON_OBJECT('title', '求资源有新回答', 'content', '你的求资源帖子收到 1 条新回答。'), 'SENT', NOW(3)),
    (11, 'RESOURCE_UPDATE', 'RESOURCE', 9, 1, JSON_OBJECT('title', '收藏资源更新', 'content', 'UI 设计模板合集新增了组件库附件。'), 'SENT', NOW(3))
ON DUPLICATE KEY UPDATE
    payload = VALUES(payload),
    status = VALUES(status),
    process_time = VALUES(process_time);

INSERT INTO system_notice (
    id, event_id, receiver_id, notice_type, title, content, target_type, target_id, is_read
) VALUES
    (10, 10, 1, 'REQUEST_REPLY', '求资源有新回答', '你的求资源帖子收到 1 条新回答。', 'REQUEST_POST', 1, 0),
    (11, 11, 1, 'RESOURCE_UPDATE', '收藏资源更新', 'UI 设计模板合集新增了组件库附件。', 'RESOURCE', 9, 1)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    content = VALUES(content),
    is_read = VALUES(is_read),
    deleted_at = NULL;

INSERT INTO login_record (
    id, account_id, login_account, result, fail_reason, login_ip, user_agent, created_at
) VALUES
    (10, 1, 'demo_user', 'SUCCESS', NULL, '192.168.1.22', 'Chrome / Windows', DATE_SUB(NOW(3), INTERVAL 1 DAY)),
    (11, 1, 'demo_user', 'SUCCESS', NULL, '192.168.1.18', 'Edge / Windows', DATE_SUB(NOW(3), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE
    result = VALUES(result),
    fail_reason = VALUES(fail_reason),
    login_ip = VALUES(login_ip),
    user_agent = VALUES(user_agent),
    created_at = VALUES(created_at);
