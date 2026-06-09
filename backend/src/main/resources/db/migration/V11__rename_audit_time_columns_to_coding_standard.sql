-- 按编码规范统一审计时间字段命名。
-- 旧库从 create_time/update_time 平滑升级为 created_at/updated_at；
-- 新库若已由新版 V1 创建标准字段，本迁移会自动跳过。

DELIMITER //

CREATE PROCEDURE rename_column_if_exists(
    IN table_name_value VARCHAR(64),
    IN old_column_value VARCHAR(64),
    IN new_column_value VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = old_column_value
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = new_column_value
    ) THEN
        SET @rename_sql = CONCAT(
            'ALTER TABLE `', table_name_value, '` RENAME COLUMN `',
            old_column_value, '` TO `', new_column_value, '`'
        );
        PREPARE rename_statement FROM @rename_sql;
        EXECUTE rename_statement;
        DEALLOCATE PREPARE rename_statement;
    END IF;
END//

CALL rename_column_if_exists('user_account', 'create_time', 'created_at')//
CALL rename_column_if_exists('user_account', 'update_time', 'updated_at')//
CALL rename_column_if_exists('membership_level', 'create_time', 'created_at')//
CALL rename_column_if_exists('membership_level', 'update_time', 'updated_at')//
CALL rename_column_if_exists('member_profile', 'create_time', 'created_at')//
CALL rename_column_if_exists('member_profile', 'update_time', 'updated_at')//
CALL rename_column_if_exists('administrator_profile', 'create_time', 'created_at')//
CALL rename_column_if_exists('administrator_profile', 'update_time', 'updated_at')//
CALL rename_column_if_exists('login_record', 'create_time', 'created_at')//
CALL rename_column_if_exists('member_point_account', 'create_time', 'created_at')//
CALL rename_column_if_exists('member_point_account', 'update_time', 'updated_at')//
CALL rename_column_if_exists('point_flow', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_category', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_category', 'update_time', 'updated_at')//
CALL rename_column_if_exists('tag_info', 'create_time', 'created_at')//
CALL rename_column_if_exists('tag_info', 'update_time', 'updated_at')//
CALL rename_column_if_exists('resource_info', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_info', 'update_time', 'updated_at')//
CALL rename_column_if_exists('resource_version', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_tag_rel', 'create_time', 'created_at')//
CALL rename_column_if_exists('file_attachment', 'create_time', 'created_at')//
CALL rename_column_if_exists('file_attachment', 'update_time', 'updated_at')//
CALL rename_column_if_exists('resource_audit_record', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_status_log', 'create_time', 'created_at')//
CALL rename_column_if_exists('download_record', 'create_time', 'created_at')//
CALL rename_column_if_exists('user_interaction', 'create_time', 'created_at')//
CALL rename_column_if_exists('user_interaction', 'update_time', 'updated_at')//
CALL rename_column_if_exists('comment_info', 'create_time', 'created_at')//
CALL rename_column_if_exists('comment_info', 'update_time', 'updated_at')//
CALL rename_column_if_exists('resource_rating', 'create_time', 'created_at')//
CALL rename_column_if_exists('request_post', 'create_time', 'created_at')//
CALL rename_column_if_exists('request_post', 'update_time', 'updated_at')//
CALL rename_column_if_exists('request_tag_rel', 'create_time', 'created_at')//
CALL rename_column_if_exists('request_reply', 'create_time', 'created_at')//
CALL rename_column_if_exists('request_reply', 'update_time', 'updated_at')//
CALL rename_column_if_exists('request_status_log', 'create_time', 'created_at')//
CALL rename_column_if_exists('report_complaint', 'create_time', 'created_at')//
CALL rename_column_if_exists('report_complaint', 'update_time', 'updated_at')//
CALL rename_column_if_exists('appeal_record', 'create_time', 'created_at')//
CALL rename_column_if_exists('appeal_record', 'update_time', 'updated_at')//
CALL rename_column_if_exists('notification_event', 'create_time', 'created_at')//
CALL rename_column_if_exists('system_notice', 'create_time', 'created_at')//
CALL rename_column_if_exists('admin_operation_log', 'create_time', 'created_at')//
CALL rename_column_if_exists('system_config', 'create_time', 'created_at')//
CALL rename_column_if_exists('system_config', 'update_time', 'updated_at')//
CALL rename_column_if_exists('platform_announcement', 'create_time', 'created_at')//
CALL rename_column_if_exists('platform_announcement', 'update_time', 'updated_at')//
CALL rename_column_if_exists('email_verification_code', 'create_time', 'created_at')//
CALL rename_column_if_exists('sensitive_word', 'create_time', 'created_at')//
CALL rename_column_if_exists('sensitive_word', 'update_time', 'updated_at')//
CALL rename_column_if_exists('search_log', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_daily_stat', 'create_time', 'created_at')//
CALL rename_column_if_exists('resource_daily_stat', 'update_time', 'updated_at')//
CALL rename_column_if_exists('member_daily_stat', 'create_time', 'created_at')//
CALL rename_column_if_exists('member_daily_stat', 'update_time', 'updated_at')//

DROP PROCEDURE rename_column_if_exists//

DELIMITER ;
