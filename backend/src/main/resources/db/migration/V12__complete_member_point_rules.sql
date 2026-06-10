-- Complete member point and reward lifecycle fields used by the V1 member point rules.

DELIMITER //

CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = column_name_value
    ) THEN
        SET @add_column_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `', column_name_value, '` ', column_definition_value);
        PREPARE add_column_statement FROM @add_column_sql;
        EXECUTE add_column_statement;
        DEALLOCATE PREPARE add_column_statement;
    END IF;
END//

CREATE PROCEDURE add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN index_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND INDEX_NAME = index_name_value
    ) THEN
        SET @add_index_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
        PREPARE add_index_statement FROM @add_index_sql;
        EXECUTE add_index_statement;
        DEALLOCATE PREPARE add_index_statement;
    END IF;
END//

CALL add_column_if_missing('point_flow', 'biz_key', 'VARCHAR(120) NULL AFTER description')//
CALL add_index_if_missing('point_flow', 'uk_point_flow_biz_key', 'UNIQUE KEY `uk_point_flow_biz_key` (`biz_key`)')//

CALL add_column_if_missing('request_post', 'reward_status', 'VARCHAR(30) NOT NULL DEFAULT ''NONE'' AFTER reward_points')//
UPDATE request_post
SET reward_status = CASE
    WHEN reward_points = 0 THEN 'NONE'
    WHEN status = 'RESOLVED' THEN 'PAID'
    WHEN status = 'CANCELLED' THEN 'RETURNED'
    ELSE 'FROZEN'
END
WHERE reward_status IS NULL OR reward_status = 'NONE'//

CALL add_column_if_missing('membership_level', 'daily_resource_publish_limit', 'INT UNSIGNED NOT NULL DEFAULT 5 AFTER daily_download_limit')//
CALL add_column_if_missing('membership_level', 'daily_request_publish_limit', 'INT UNSIGNED NOT NULL DEFAULT 5 AFTER daily_resource_publish_limit')//

DROP PROCEDURE add_index_if_missing//
DROP PROCEDURE add_column_if_missing//

DELIMITER ;

INSERT INTO system_config (
    config_key, config_value, value_type, description, is_sensitive, is_enabled
) VALUES
    ('point.daily_login', '10', 'INTEGER', '每日登录 + 积分', 0, 1),
    ('point.resource_favorited', '5', 'INTEGER', '资源被收藏 + 积分', 0, 1),
    ('point.resource_liked', '3', 'INTEGER', '资源被点赞 + 积分', 0, 1),
    ('point.resource_approved', '10', 'INTEGER', '资源审核通过 + 积分', 0, 1),
    ('point.resource_downloaded', '5', 'INTEGER', '资源被下载 + 积分', 0, 1),
    ('point.request_accepted', '10', 'INTEGER', '回答被采纳 + 平台奖励', 0, 1),
    ('point.violation_penalty', '10', 'INTEGER', '举报成立通用扣分', 0, 1),
    ('point.resource_offline_penalty', '20', 'INTEGER', '资源违规下架扣分', 0, 1),
    ('point.comment_delete_penalty', '5', 'INTEGER', '评论违规删除扣分', 0, 1),
    ('resource.daily_publish_limit', '5', 'INTEGER', '默认每日资源发布上限', 0, 1),
    ('request.daily_publish_limit', '5', 'INTEGER', '默认每日求资源发布上限', 0, 1)
ON DUPLICATE KEY UPDATE
    value_type = VALUES(value_type),
    description = VALUES(description),
    is_sensitive = VALUES(is_sensitive),
    is_enabled = VALUES(is_enabled);
