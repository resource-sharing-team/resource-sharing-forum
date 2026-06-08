-- Complete design-spec state machine values and operational indexes.

ALTER TABLE resource_info
    DROP CHECK ck_resource_status;

ALTER TABLE resource_info
    ADD CONSTRAINT ck_resource_status
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'REVIEWING_RISK', 'OFFLINE', 'COPYRIGHT_DOWN', 'DELETED'));

ALTER TABLE resource_audit_record
    DROP CHECK ck_audit_result;

ALTER TABLE resource_audit_record
    ADD CONSTRAINT ck_audit_result
        CHECK (audit_result IN ('APPROVED', 'REJECTED', 'OFFLINE', 'RESTORED', 'RISK_REVIEW', 'COPYRIGHT_DOWN', 'DELETED'));

CREATE INDEX idx_notice_receiver_time
    ON system_notice (receiver_id, create_time);

CREATE INDEX idx_attachment_owner_status
    ON file_attachment (owner_type, owner_id, status, deleted_at);

CREATE INDEX idx_point_flow_idempotency
    ON point_flow (member_id, scene, related_type, related_id, flow_type);
