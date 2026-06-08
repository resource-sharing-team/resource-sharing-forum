-- Keep demo tags derived from the controlled taxonomy only:
-- first-level category + second-level category + resource type.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

INSERT INTO tag_info (tag_name, use_count, status)
VALUES
    ('文档资料', 0, 'ENABLED'),
    ('设计素材', 0, 'ENABLED'),
    ('源码模板', 0, 'ENABLED'),
    ('教程学习', 0, 'ENABLED'),
    ('软件工具', 0, 'ENABLED'),
    ('考试资料', 0, 'ENABLED'),
    ('办公模板', 0, 'ENABLED'),
    ('学习笔记', 0, 'ENABLED'),
    ('UI设计', 0, 'ENABLED'),
    ('图片素材', 0, 'ENABLED'),
    ('字体图标', 0, 'ENABLED'),
    ('前端源码', 0, 'ENABLED'),
    ('后端源码', 0, 'ENABLED'),
    ('完整项目', 0, 'ENABLED'),
    ('IT教程', 0, 'ENABLED'),
    ('办公教程', 0, 'ENABLED'),
    ('设计教程', 0, 'ENABLED'),
    ('开发工具', 0, 'ENABLED'),
    ('设计工具', 0, 'ENABLED'),
    ('效率工具', 0, 'ENABLED'),
    ('文档', 0, 'ENABLED'),
    ('软件', 0, 'ENABLED'),
    ('源码', 0, 'ENABLED'),
    ('素材', 0, 'ENABLED'),
    ('教程', 0, 'ENABLED'),
    ('模板', 0, 'ENABLED'),
    ('链接', 0, 'ENABLED')
ON DUPLICATE KEY UPDATE
    status = 'ENABLED',
    deleted_at = NULL;

INSERT INTO resource_info (
    id, publisher_id, category_id, title, resource_type, summary, description,
    external_url, status, view_count, download_count, favorite_count, like_count,
    comment_count, rating_count, average_rating, current_version_no, submitted_time, published_time
) VALUES
    (14, 8, 13, '软件工程课程学习笔记', 'DOCUMENT', '按章节整理的软件工程课程笔记，适合复习和课程设计说明书编写。', '内容包含需求分析、概要设计、详细设计、测试计划和项目管理知识点，按文档模板结构整理。', NULL, 'PUBLISHED', 420, 86, 18, 11, 0, 42, 4.60, 1, DATE_SUB(NOW(3), INTERVAL 8 DAY), DATE_SUB(NOW(3), INTERVAL 7 DAY)),
    (15, 7, 23, '常用界面字体图标素材包', 'MATERIAL', '整理常用字体图标与按钮状态素材，适合 Web 和移动端原型设计。', '素材按字体、图标、按钮状态和空状态分组，附带使用说明，便于前端页面快速搭建。', NULL, 'PUBLISHED', 360, 104, 22, 16, 0, 51, 4.70, 1, DATE_SUB(NOW(3), INTERVAL 7 DAY), DATE_SUB(NOW(3), INTERVAL 6 DAY)),
    (16, 15, 32, 'Spring Boot 接口服务源码模板', 'SOURCE_CODE', '包含认证、资源管理、评论和后台日志模块的后端源码模板。', '项目按 controller/service/support 分层，包含统一响应、JWT 鉴权、基础测试和 MySQL 配置示例。', NULL, 'PUBLISHED', 610, 188, 34, 20, 0, 76, 4.80, 1, DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY)),
    (17, 10, 33, '校园资源分享完整项目源码', 'SOURCE_CODE', '前后端联调版完整项目源码，适合课程设计和毕业设计参考。', '包含用户端、管理端、后端接口、数据库迁移和部署说明，按模块拆分并保留演示数据。', NULL, 'PUBLISHED', 780, 236, 41, 28, 0, 89, 4.90, 1, DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 5 DAY)),
    (18, 9, 42, 'Excel 数据透视表办公教程', 'COURSE', '面向课程汇报和日常办公的数据透视表教程资料。', '教程包含示例表格、步骤截图和练习数据，覆盖筛选、分组、图表和统计汇总。', NULL, 'PUBLISHED', 330, 92, 14, 8, 0, 37, 4.50, 1, DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY)),
    (19, 14, 43, 'Figma 组件化设计教程', 'COURSE', '从组件、变量到设计规范落地的 Figma 教程资料。', '教程用后台管理页面作为案例，讲解颜色、字体、间距、组件状态和交付标注。', NULL, 'PUBLISHED', 520, 147, 29, 19, 0, 63, 4.70, 1, DATE_SUB(NOW(3), INTERVAL 5 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY)),
    (20, 15, 51, '开发环境配置工具清单', 'SOFTWARE', '整理 Java、Node、MySQL、Git 等开发环境安装与配置工具。', '内容包含 Windows 本地开发环境配置步骤、常见端口说明、安装包清单和故障排查表。', NULL, 'PUBLISHED', 450, 128, 16, 12, 0, 48, 4.60, 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY)),
    (21, 7, 52, '设计工具插件合集', 'SOFTWARE', '整理原型设计和界面设计常用插件及配置说明。', '包含图标、标注、切图、配色和组件检查插件说明，适合原型与前端协作演示。', NULL, 'PUBLISHED', 390, 119, 20, 10, 0, 44, 4.60, 1, DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY)),
    (22, 13, 53, '学习计划效率工具包', 'SOFTWARE', '课程复习、资料整理和项目排期常用效率工具集合。', '工具包包含学习计划表、待办看板、番茄钟配置和资源整理模板，适合个人中心演示。', NULL, 'PUBLISHED', 410, 133, 19, 13, 0, 46, 4.60, 1, DATE_SUB(NOW(3), INTERVAL 3 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY)),
    (23, 15, 51, '在线 API 调试工具导航', 'LINK', '汇总常用在线 API 调试、JSON 格式化和接口文档工具链接。', '链接按接口调试、Mock 数据、JSON 校验、状态码查询和文档生成分类整理。', 'https://example.com/api-tools', 'PUBLISHED', 280, 64, 12, 7, 0, 21, 4.40, 1, DATE_SUB(NOW(3), INTERVAL 3 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE
    publisher_id = VALUES(publisher_id),
    category_id = VALUES(category_id),
    title = VALUES(title),
    resource_type = VALUES(resource_type),
    summary = VALUES(summary),
    description = VALUES(description),
    external_url = VALUES(external_url),
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
    (14, 14, 1, '软件工程课程学习笔记', '按章节整理的软件工程课程笔记，适合复习和课程设计说明书编写。', '内容包含需求分析、概要设计、详细设计、测试计划和项目管理知识点，按文档模板结构整理。', 13, 'DOCUMENT', NULL, JSON_ARRAY('文档资料', '学习笔记', '文档'), JSON_ARRAY(JSON_OBJECT('fileName', 'software-engineering-notes.pdf', 'fileSize', 7340032)), 8),
    (15, 15, 1, '常用界面字体图标素材包', '整理常用字体图标与按钮状态素材，适合 Web 和移动端原型设计。', '素材按字体、图标、按钮状态和空状态分组，附带使用说明，便于前端页面快速搭建。', 23, 'MATERIAL', NULL, JSON_ARRAY('设计素材', '字体图标', '素材'), JSON_ARRAY(JSON_OBJECT('fileName', 'font-icon-assets.zip', 'fileSize', 18874368)), 7),
    (16, 16, 1, 'Spring Boot 接口服务源码模板', '包含认证、资源管理、评论和后台日志模块的后端源码模板。', '项目按 controller/service/support 分层，包含统一响应、JWT 鉴权、基础测试和 MySQL 配置示例。', 32, 'SOURCE_CODE', NULL, JSON_ARRAY('源码模板', '后端源码', '源码'), JSON_ARRAY(JSON_OBJECT('fileName', 'springboot-api-template.zip', 'fileSize', 29360128)), 15),
    (17, 17, 1, '校园资源分享完整项目源码', '前后端联调版完整项目源码，适合课程设计和毕业设计参考。', '包含用户端、管理端、后端接口、数据库迁移和部署说明，按模块拆分并保留演示数据。', 33, 'SOURCE_CODE', NULL, JSON_ARRAY('源码模板', '完整项目', '源码'), JSON_ARRAY(JSON_OBJECT('fileName', 'campus-resource-forum-full.zip', 'fileSize', 52428800)), 10),
    (18, 18, 1, 'Excel 数据透视表办公教程', '面向课程汇报和日常办公的数据透视表教程资料。', '教程包含示例表格、步骤截图和练习数据，覆盖筛选、分组、图表和统计汇总。', 42, 'COURSE', NULL, JSON_ARRAY('教程学习', '办公教程', '教程'), JSON_ARRAY(JSON_OBJECT('fileName', 'excel-pivot-course.zip', 'fileSize', 15728640)), 9),
    (19, 19, 1, 'Figma 组件化设计教程', '从组件、变量到设计规范落地的 Figma 教程资料。', '教程用后台管理页面作为案例，讲解颜色、字体、间距、组件状态和交付标注。', 43, 'COURSE', NULL, JSON_ARRAY('教程学习', '设计教程', '教程'), JSON_ARRAY(JSON_OBJECT('fileName', 'figma-component-course.zip', 'fileSize', 25165824)), 14),
    (20, 20, 1, '开发环境配置工具清单', '整理 Java、Node、MySQL、Git 等开发环境安装与配置工具。', '内容包含 Windows 本地开发环境配置步骤、常见端口说明、安装包清单和故障排查表。', 51, 'SOFTWARE', NULL, JSON_ARRAY('软件工具', '开发工具', '软件'), JSON_ARRAY(JSON_OBJECT('fileName', 'dev-tools-checklist.zip', 'fileSize', 6291456)), 15),
    (21, 21, 1, '设计工具插件合集', '整理原型设计和界面设计常用插件及配置说明。', '包含图标、标注、切图、配色和组件检查插件说明，适合原型与前端协作演示。', 52, 'SOFTWARE', NULL, JSON_ARRAY('软件工具', '设计工具', '软件'), JSON_ARRAY(JSON_OBJECT('fileName', 'design-tool-plugins.zip', 'fileSize', 12582912)), 7),
    (22, 22, 1, '学习计划效率工具包', '课程复习、资料整理和项目排期常用效率工具集合。', '工具包包含学习计划表、待办看板、番茄钟配置和资源整理模板，适合个人中心演示。', 53, 'SOFTWARE', NULL, JSON_ARRAY('软件工具', '效率工具', '软件'), JSON_ARRAY(JSON_OBJECT('fileName', 'study-productivity-tools.zip', 'fileSize', 8388608)), 13),
    (23, 23, 1, '在线 API 调试工具导航', '汇总常用在线 API 调试、JSON 格式化和接口文档工具链接。', '链接按接口调试、Mock 数据、JSON 校验、状态码查询和文档生成分类整理。', 51, 'LINK', 'https://example.com/api-tools', JSON_ARRAY('软件工具', '开发工具', '链接'), JSON_ARRAY(JSON_OBJECT('fileName', 'api-tools-links.md', 'fileSize', 65536)), 15)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    description = VALUES(description),
    category_id = VALUES(category_id),
    resource_type = VALUES(resource_type),
    external_url = VALUES(external_url),
    tag_snapshot = VALUES(tag_snapshot),
    attachment_snapshot = VALUES(attachment_snapshot);

INSERT INTO file_attachment (
    id, owner_type, owner_id, uploader_id, original_file_name, stored_file_name,
    file_ext, mime_type, file_size, file_hash, storage_path, status, download_count
) VALUES
    (31, 'RESOURCE', 14, 9, '软件工程课程学习笔记.pdf', 'resource-14-notes.pdf', 'pdf', 'application/pdf', 7340032, 'demo-file-31', './uploads/resource/14/resource-14-notes.pdf', 'NORMAL', 86),
    (32, 'RESOURCE', 15, 8, '常用界面字体图标素材包.zip', 'resource-15-icons.zip', 'zip', 'application/zip', 18874368, 'demo-file-32', './uploads/resource/15/resource-15-icons.zip', 'NORMAL', 104),
    (33, 'RESOURCE', 16, 16, 'Spring Boot 接口服务源码模板.zip', 'resource-16-source.zip', 'zip', 'application/zip', 29360128, 'demo-file-33', './uploads/resource/16/resource-16-source.zip', 'NORMAL', 188),
    (34, 'RESOURCE', 17, 11, '校园资源分享完整项目源码.zip', 'resource-17-full.zip', 'zip', 'application/zip', 52428800, 'demo-file-34', './uploads/resource/17/resource-17-full.zip', 'NORMAL', 236),
    (35, 'RESOURCE', 18, 10, 'Excel 数据透视表办公教程.zip', 'resource-18-course.zip', 'zip', 'application/zip', 15728640, 'demo-file-35', './uploads/resource/18/resource-18-course.zip', 'NORMAL', 92),
    (36, 'RESOURCE', 19, 15, 'Figma 组件化设计教程.zip', 'resource-19-course.zip', 'zip', 'application/zip', 25165824, 'demo-file-36', './uploads/resource/19/resource-19-course.zip', 'NORMAL', 147),
    (37, 'RESOURCE', 20, 16, '开发环境配置工具清单.zip', 'resource-20-tools.zip', 'zip', 'application/zip', 6291456, 'demo-file-37', './uploads/resource/20/resource-20-tools.zip', 'NORMAL', 128),
    (38, 'RESOURCE', 21, 8, '设计工具插件合集.zip', 'resource-21-plugins.zip', 'zip', 'application/zip', 12582912, 'demo-file-38', './uploads/resource/21/resource-21-plugins.zip', 'NORMAL', 119),
    (39, 'RESOURCE', 22, 14, '学习计划效率工具包.zip', 'resource-22-productivity.zip', 'zip', 'application/zip', 8388608, 'demo-file-39', './uploads/resource/22/resource-22-productivity.zip', 'NORMAL', 133),
    (40, 'RESOURCE', 23, 16, '在线 API 调试工具导航.md', 'resource-23-links.md', 'md', 'text/markdown', 65536, 'demo-file-40', './uploads/resource/23/resource-23-links.md', 'NORMAL', 64)
ON DUPLICATE KEY UPDATE
    owner_id = VALUES(owner_id),
    original_file_name = VALUES(original_file_name),
    stored_file_name = VALUES(stored_file_name),
    file_ext = VALUES(file_ext),
    mime_type = VALUES(mime_type),
    file_size = VALUES(file_size),
    storage_path = VALUES(storage_path),
    status = VALUES(status),
    download_count = VALUES(download_count),
    deleted_at = NULL;

INSERT INTO request_post (
    id, requester_id, category_id, title, content, expected_format,
    reward_points, status, accepted_reply_id, view_count, answer_count, comment_count, resolved_time, closed_time
) VALUES
    (14, 12, 13, '求软件工程课程学习笔记', '需要一份按章节整理的软件工程课程学习笔记，用于复习需求分析、概要设计、详细设计和测试相关内容。', '文档', 20, 'ONGOING', NULL, 90, 0, 0, NULL, NULL),
    (15, 14, 23, '求可商用字体图标素材', '需要一套可以用于课程原型和前端页面的字体图标素材，最好包含授权说明和常用按钮状态。', '素材', 30, 'ONGOING', NULL, 78, 0, 0, NULL, NULL),
    (16, 15, 32, '求 Spring Boot 后端源码模板', '需要包含登录鉴权、资源管理、评论和后台日志模块的 Spring Boot 后端源码模板，方便课程设计参考。', '源码', 80, 'ONGOING', NULL, 120, 0, 0, NULL, NULL),
    (17, 13, 33, '求完整项目源码和部署说明', '需要一套前后端联调完整项目源码，最好包含数据库迁移、部署说明和演示数据，便于最终答辩展示。', '源码', 120, 'ONGOING', NULL, 150, 0, 0, NULL, NULL),
    (18, 10, 42, '求 Excel 办公教程资料', '需要一份面向办公场景的 Excel 教程资料，重点讲数据透视表、筛选、图表和统计汇总。', '教程', 40, 'ONGOING', NULL, 66, 0, 0, NULL, NULL),
    (19, 11, 52, '求设计工具插件合集', '需要整理原型设计和界面设计常用插件，最好说明用途、安装方式和适合的页面设计场景。', '软件', 35, 'ONGOING', NULL, 70, 0, 0, NULL, NULL),
    (20, 9, 53, '求学习计划效率工具', '需要适合课程复习和项目排期的效率工具或模板，能够管理资料、任务和阶段性目标。', '软件', 25, 'ONGOING', NULL, 85, 0, 0, NULL, NULL),
    (21, 15, 51, '求在线 API 调试工具链接', '需要常用在线 API 调试、JSON 格式化和接口文档工具链接，方便前后端联调时快速验证接口。', '链接', 15, 'ONGOING', NULL, 55, 0, 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
    requester_id = VALUES(requester_id),
    category_id = VALUES(category_id),
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

UPDATE request_post
SET expected_format = CASE id
    WHEN 1 THEN '源码'
    WHEN 2 THEN '源码'
    WHEN 3 THEN '文档'
    WHEN 4 THEN '源码'
    WHEN 10 THEN '文档'
    WHEN 11 THEN '源码'
    WHEN 12 THEN '文档'
    WHEN 13 THEN '教程'
    ELSE expected_format
END
WHERE id IN (1, 2, 3, 4, 10, 11, 12, 13);

DELETE FROM resource_tag_rel WHERE resource_id BETWEEN 1 AND 23;
DELETE FROM request_tag_rel WHERE request_id IN (1, 2, 3, 4) OR request_id BETWEEN 10 AND 21;

INSERT INTO resource_tag_rel (resource_id, tag_id)
SELECT normalized.resource_id, ti.id
FROM (
    SELECT 1 resource_id, '文档资料' tag_name UNION ALL SELECT 1, '考试资料' UNION ALL SELECT 1, '文档'
    UNION ALL SELECT 2, '设计素材' UNION ALL SELECT 2, 'UI设计' UNION ALL SELECT 2, '素材'
    UNION ALL SELECT 3, '文档资料' UNION ALL SELECT 3, '办公模板' UNION ALL SELECT 3, '模板'
    UNION ALL SELECT 4, '设计素材' UNION ALL SELECT 4, 'UI设计' UNION ALL SELECT 4, '素材'
    UNION ALL SELECT 5, '教程学习' UNION ALL SELECT 5, 'IT教程' UNION ALL SELECT 5, '教程'
    UNION ALL SELECT 6, '教程学习' UNION ALL SELECT 6, 'IT教程' UNION ALL SELECT 6, '教程'
    UNION ALL SELECT 7, '文档资料' UNION ALL SELECT 7, '考试资料' UNION ALL SELECT 7, '文档'
    UNION ALL SELECT 8, '源码模板' UNION ALL SELECT 8, '前端源码' UNION ALL SELECT 8, '源码'
    UNION ALL SELECT 9, '设计素材' UNION ALL SELECT 9, 'UI设计' UNION ALL SELECT 9, '素材'
    UNION ALL SELECT 10, '教程学习' UNION ALL SELECT 10, 'IT教程' UNION ALL SELECT 10, '教程'
    UNION ALL SELECT 11, '文档资料' UNION ALL SELECT 11, '办公模板' UNION ALL SELECT 11, '模板'
    UNION ALL SELECT 12, '源码模板' UNION ALL SELECT 12, '前端源码' UNION ALL SELECT 12, '源码'
    UNION ALL SELECT 13, '设计素材' UNION ALL SELECT 13, '图片素材' UNION ALL SELECT 13, '素材'
    UNION ALL SELECT 14, '文档资料' UNION ALL SELECT 14, '学习笔记' UNION ALL SELECT 14, '文档'
    UNION ALL SELECT 15, '设计素材' UNION ALL SELECT 15, '字体图标' UNION ALL SELECT 15, '素材'
    UNION ALL SELECT 16, '源码模板' UNION ALL SELECT 16, '后端源码' UNION ALL SELECT 16, '源码'
    UNION ALL SELECT 17, '源码模板' UNION ALL SELECT 17, '完整项目' UNION ALL SELECT 17, '源码'
    UNION ALL SELECT 18, '教程学习' UNION ALL SELECT 18, '办公教程' UNION ALL SELECT 18, '教程'
    UNION ALL SELECT 19, '教程学习' UNION ALL SELECT 19, '设计教程' UNION ALL SELECT 19, '教程'
    UNION ALL SELECT 20, '软件工具' UNION ALL SELECT 20, '开发工具' UNION ALL SELECT 20, '软件'
    UNION ALL SELECT 21, '软件工具' UNION ALL SELECT 21, '设计工具' UNION ALL SELECT 21, '软件'
    UNION ALL SELECT 22, '软件工具' UNION ALL SELECT 22, '效率工具' UNION ALL SELECT 22, '软件'
    UNION ALL SELECT 23, '软件工具' UNION ALL SELECT 23, '开发工具' UNION ALL SELECT 23, '链接'
) normalized
JOIN tag_info ti ON ti.tag_name = normalized.tag_name
ON DUPLICATE KEY UPDATE resource_id = VALUES(resource_id);

INSERT INTO request_tag_rel (request_id, tag_id)
SELECT normalized.request_id, ti.id
FROM (
    SELECT 1 request_id, '源码模板' tag_name UNION ALL SELECT 1, '后端源码' UNION ALL SELECT 1, '源码'
    UNION ALL SELECT 2, '源码模板' UNION ALL SELECT 2, '前端源码' UNION ALL SELECT 2, '源码'
    UNION ALL SELECT 3, '文档资料' UNION ALL SELECT 3, '办公模板' UNION ALL SELECT 3, '文档'
    UNION ALL SELECT 4, '源码模板' UNION ALL SELECT 4, '前端源码' UNION ALL SELECT 4, '源码'
    UNION ALL SELECT 10, '文档资料' UNION ALL SELECT 10, '考试资料' UNION ALL SELECT 10, '文档'
    UNION ALL SELECT 11, '教程学习' UNION ALL SELECT 11, 'IT教程' UNION ALL SELECT 11, '源码'
    UNION ALL SELECT 12, '设计素材' UNION ALL SELECT 12, 'UI设计' UNION ALL SELECT 12, '文档'
    UNION ALL SELECT 13, '教程学习' UNION ALL SELECT 13, 'IT教程' UNION ALL SELECT 13, '教程'
    UNION ALL SELECT 14, '文档资料' UNION ALL SELECT 14, '学习笔记' UNION ALL SELECT 14, '文档'
    UNION ALL SELECT 15, '设计素材' UNION ALL SELECT 15, '字体图标' UNION ALL SELECT 15, '素材'
    UNION ALL SELECT 16, '源码模板' UNION ALL SELECT 16, '后端源码' UNION ALL SELECT 16, '源码'
    UNION ALL SELECT 17, '源码模板' UNION ALL SELECT 17, '完整项目' UNION ALL SELECT 17, '源码'
    UNION ALL SELECT 18, '教程学习' UNION ALL SELECT 18, '办公教程' UNION ALL SELECT 18, '教程'
    UNION ALL SELECT 19, '软件工具' UNION ALL SELECT 19, '设计工具' UNION ALL SELECT 19, '软件'
    UNION ALL SELECT 20, '软件工具' UNION ALL SELECT 20, '效率工具' UNION ALL SELECT 20, '软件'
    UNION ALL SELECT 21, '软件工具' UNION ALL SELECT 21, '开发工具' UNION ALL SELECT 21, '链接'
) normalized
JOIN tag_info ti ON ti.tag_name = normalized.tag_name
ON DUPLICATE KEY UPDATE request_id = VALUES(request_id);

UPDATE resource_version rv
JOIN resource_info r ON r.id = rv.resource_id
LEFT JOIN resource_category c2 ON c2.id = r.category_id
LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
SET rv.tag_snapshot = JSON_ARRAY(
        c1.category_name,
        c2.category_name,
        CASE r.resource_type
            WHEN 'SOFTWARE' THEN '软件'
            WHEN 'SOURCE_CODE' THEN '源码'
            WHEN 'MATERIAL' THEN '素材'
            WHEN 'COURSE' THEN '教程'
            WHEN 'TEMPLATE' THEN '模板'
            WHEN 'LINK' THEN '链接'
            ELSE '文档'
        END
    )
WHERE rv.resource_id BETWEEN 1 AND 23;

UPDATE tag_info ti
LEFT JOIN (
    SELECT tag_id, COUNT(*) AS relation_count
    FROM (
        SELECT tag_id FROM resource_tag_rel
        UNION ALL
        SELECT tag_id FROM request_tag_rel
    ) rel
    GROUP BY tag_id
) usage_count ON usage_count.tag_id = ti.id
SET ti.use_count = COALESCE(usage_count.relation_count, 0)
WHERE ti.tag_name IN (
    '文档资料', '设计素材', '源码模板', '教程学习', '软件工具',
    '考试资料', '办公模板', '学习笔记', 'UI设计', '图片素材', '字体图标',
    '前端源码', '后端源码', '完整项目', 'IT教程', '办公教程', '设计教程',
    '开发工具', '设计工具', '效率工具',
    '文档', '软件', '源码', '素材', '教程', '模板', '链接'
);

UPDATE tag_info
SET status = 'DISABLED',
    use_count = 0
WHERE id BETWEEN 1 AND 35
  AND tag_name NOT IN (
      '文档资料', '设计素材', '源码模板', '教程学习', '软件工具',
      '考试资料', '办公模板', '学习笔记', 'UI设计', '图片素材', '字体图标',
      '前端源码', '后端源码', '完整项目', 'IT教程', '办公教程', '设计教程',
      '开发工具', '设计工具', '效率工具',
      '文档', '软件', '源码', '素材', '教程', '模板', '链接'
  );
