-- Harden design-spec workflow integrity without changing existing V1-V4 migrations.

ALTER TABLE appeal_record
    ADD COLUMN pending_target_key VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'PENDING' AND deleted_at IS NULL
                    THEN CONCAT(target_type, ':', target_id)
                ELSE NULL
            END
        ) STORED;

CREATE UNIQUE INDEX uk_appeal_pending_target
    ON appeal_record (pending_target_key);

ALTER TABLE report_complaint
    ADD COLUMN active_report_key VARCHAR(220)
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('PENDING', 'PROCESSING') AND deleted_at IS NULL
                    THEN CONCAT(COALESCE(reporter_id, 0), ':', target_type, ':', target_id, ':', report_type)
                ELSE NULL
            END
        ) STORED;

CREATE UNIQUE INDEX uk_report_active_target
    ON report_complaint (active_report_key);

CREATE INDEX idx_download_member_resource_success_time
    ON download_record (member_id, resource_id, status, created_at);

CREATE INDEX idx_notice_receiver_unread_time
    ON system_notice (receiver_id, is_read, deleted_at, created_at);

CREATE INDEX idx_attachment_uploader_status
    ON file_attachment (uploader_id, status, deleted_at);

CREATE INDEX idx_admin_log_target_time
    ON admin_operation_log (target_type, target_id, created_at);

CREATE INDEX idx_request_deadline_status
    ON request_post (status, deadline_time);

CREATE INDEX idx_point_flow_related_scene
    ON point_flow (related_type, related_id, scene, flow_type);
