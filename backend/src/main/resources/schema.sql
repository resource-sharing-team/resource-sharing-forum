-- Resource Sharing Forum V2 database schema.
-- Target database: MySQL 8.x, charset utf8mb4.
-- This script is intended for an empty development database.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    status VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    last_login_time DATETIME(3) NULL,
    last_login_ip VARCHAR(64) NULL,
    password_changed_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_role (role),
    KEY idx_user_status (status),
    CONSTRAINT ck_user_role CHECK (role IN ('USER', 'AUDITOR', 'ADMIN', 'SUPER_ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN ('NORMAL', 'LOCKED', 'DISABLED', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS membership_level (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    level_code VARCHAR(30) NOT NULL,
    level_name VARCHAR(50) NOT NULL,
    min_points INT UNSIGNED NOT NULL DEFAULT 0,
    max_points INT UNSIGNED NULL,
    daily_download_limit INT UNSIGNED NOT NULL DEFAULT 10,
    daily_resource_publish_limit INT UNSIGNED NOT NULL DEFAULT 5,
    daily_request_publish_limit INT UNSIGNED NOT NULL DEFAULT 5,
    max_files_per_resource INT UNSIGNED NOT NULL DEFAULT 5,
    max_file_size_mb INT UNSIGNED NOT NULL DEFAULT 100,
    reward_limit INT UNSIGNED NOT NULL DEFAULT 100,
    can_apply_top TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_level_code (level_code),
    KEY idx_level_points (min_points, max_points),
    KEY idx_level_status (status),
    CONSTRAINT ck_level_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    gender VARCHAR(20) NULL,
    bio VARCHAR(500) NULL,
    school VARCHAR(100) NULL,
    major VARCHAR(100) NULL,
    violation_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_account (account_id),
    KEY idx_member_nickname (nickname),
    CONSTRAINT fk_member_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT ck_member_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS administrator_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    employee_no VARCHAR(50) NULL,
    permission_group VARCHAR(60) NOT NULL DEFAULT 'ADMIN',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_account (account_id),
    UNIQUE KEY uk_admin_employee_no (employee_no),
    KEY idx_admin_group (permission_group),
    CONSTRAINT fk_admin_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT ck_admin_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NULL,
    login_account VARCHAR(120) NOT NULL,
    result VARCHAR(30) NOT NULL,
    fail_reason VARCHAR(255) NULL,
    login_ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_login_account_time (login_account, created_at),
    KEY idx_login_user_time (account_id, created_at),
    KEY idx_login_result (result),
    CONSTRAINT fk_login_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT ck_login_result CHECK (result IN ('SUCCESS', 'FAILED', 'LOCKED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_point_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    level_id BIGINT UNSIGNED NOT NULL,
    current_points INT NOT NULL DEFAULT 0,
    frozen_points INT NOT NULL DEFAULT 0,
    total_earned_points INT NOT NULL DEFAULT 0,
    total_spent_points INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_member (member_id),
    KEY idx_point_level (level_id),
    KEY idx_point_current (current_points),
    CONSTRAINT fk_point_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT fk_point_level FOREIGN KEY (level_id) REFERENCES membership_level (id),
    CONSTRAINT ck_point_non_negative CHECK (current_points >= 0 AND frozen_points >= 0 AND frozen_points <= current_points)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS point_flow (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    flow_type VARCHAR(30) NOT NULL,
    scene VARCHAR(50) NOT NULL,
    points_change INT NOT NULL DEFAULT 0,
    frozen_change INT NOT NULL DEFAULT 0,
    before_points INT NOT NULL,
    after_points INT NOT NULL,
    before_frozen_points INT NOT NULL,
    after_frozen_points INT NOT NULL,
    related_type VARCHAR(50) NULL,
    related_id BIGINT UNSIGNED NULL,
    operator_id BIGINT UNSIGNED NULL,
    description VARCHAR(500) NULL,
    biz_key VARCHAR(120) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_flow_biz_key (biz_key),
    KEY idx_point_member_time (member_id, created_at),
    KEY idx_point_scene (scene),
    KEY idx_point_related (related_type, related_id),
    CONSTRAINT fk_flow_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT fk_flow_operator FOREIGN KEY (operator_id) REFERENCES user_account (id),
    CONSTRAINT ck_flow_type CHECK (flow_type IN ('EARN', 'FREEZE', 'UNFREEZE', 'TRANSFER_IN', 'TRANSFER_OUT', 'DEDUCT', 'REFUND', 'ADJUST')),
    CONSTRAINT ck_flow_balance CHECK (after_points >= 0 AND after_frozen_points >= 0 AND after_frozen_points <= after_points)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_category (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id BIGINT UNSIGNED NULL,
    category_name VARCHAR(60) NOT NULL,
    level_no TINYINT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_parent_name (parent_id, category_name),
    KEY idx_category_parent (parent_id),
    KEY idx_category_status (status),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES resource_category (id),
    CONSTRAINT ck_category_level CHECK (level_no IN (1, 2)),
    CONSTRAINT ck_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tag_info (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tag_name VARCHAR(12) NOT NULL,
    use_count INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (tag_name),
    KEY idx_tag_status (status),
    CONSTRAINT ck_tag_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_info (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    publisher_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(100) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    external_url VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    reject_reason VARCHAR(500) NULL,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    download_count INT UNSIGNED NOT NULL DEFAULT 0,
    favorite_count INT UNSIGNED NOT NULL DEFAULT 0,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    rating_count INT UNSIGNED NOT NULL DEFAULT 0,
    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    current_version_no INT UNSIGNED NOT NULL DEFAULT 1,
    submitted_time DATETIME(3) NULL,
    published_time DATETIME(3) NULL,
    offline_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_resource_status_time (status, published_time),
    KEY idx_resource_category_status (category_id, status),
    KEY idx_resource_publisher_status (publisher_id, status),
    KEY idx_resource_type_status (resource_type, status),
    KEY idx_resource_hot (status, download_count, like_count, favorite_count),
    FULLTEXT KEY ft_resource_search (title, summary, description),
    CONSTRAINT fk_resource_publisher FOREIGN KEY (publisher_id) REFERENCES member_profile (id),
    CONSTRAINT fk_resource_category FOREIGN KEY (category_id) REFERENCES resource_category (id),
    CONSTRAINT ck_resource_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'REVIEWING_RISK', 'OFFLINE', 'COPYRIGHT_DOWN', 'DELETED')),
    CONSTRAINT ck_resource_type CHECK (resource_type IN ('DOCUMENT', 'SOFTWARE', 'SOURCE_CODE', 'MATERIAL', 'COURSE', 'TEMPLATE', 'LINK')),
    CONSTRAINT ck_resource_rating CHECK (average_rating >= 0 AND average_rating <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resource_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    title VARCHAR(100) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    description MEDIUMTEXT NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    external_url VARCHAR(500) NULL,
    tag_snapshot JSON NULL,
    attachment_snapshot JSON NULL,
    submitter_id BIGINT UNSIGNED NOT NULL,
    submit_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_version (resource_id, version_no),
    KEY idx_version_resource_time (resource_id, submit_time),
    CONSTRAINT fk_version_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT fk_version_category FOREIGN KEY (category_id) REFERENCES resource_category (id),
    CONSTRAINT fk_version_submitter FOREIGN KEY (submitter_id) REFERENCES member_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_tag_rel (
    resource_id BIGINT UNSIGNED NOT NULL,
    tag_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (resource_id, tag_id),
    UNIQUE KEY uk_resource_tag (resource_id, tag_id),
    KEY idx_resource_tag_tag (tag_id),
    CONSTRAINT fk_resource_tag_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT fk_resource_tag_tag FOREIGN KEY (tag_id) REFERENCES tag_info (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS file_attachment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_type VARCHAR(40) NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    uploader_id BIGINT UNSIGNED NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_ext VARCHAR(20) NOT NULL,
    mime_type VARCHAR(120) NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    file_hash VARCHAR(128) NULL,
    storage_path VARCHAR(700) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'TEMP',
    download_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_attachment_owner (owner_type, owner_id),
    KEY idx_attachment_uploader (uploader_id),
    KEY idx_attachment_status (status),
    KEY idx_attachment_hash (file_hash),
    CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_id) REFERENCES user_account (id),
    CONSTRAINT ck_attachment_owner CHECK (owner_type IN ('RESOURCE', 'REQUEST_POST', 'REQUEST_REPLY', 'REPORT_COMPLAINT', 'APPEAL')),
    CONSTRAINT ck_attachment_status CHECK (status IN ('TEMP', 'NORMAL', 'DELETED', 'BLOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_audit_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resource_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    auditor_id BIGINT UNSIGNED NOT NULL,
    audit_result VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    audit_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_resource (resource_id),
    KEY idx_audit_auditor_time (auditor_id, audit_time),
    CONSTRAINT fk_audit_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT fk_audit_auditor FOREIGN KEY (auditor_id) REFERENCES administrator_profile (id),
    CONSTRAINT ck_audit_result CHECK (audit_result IN ('APPROVED', 'REJECTED', 'OFFLINE', 'RESTORED', 'RISK_REVIEW', 'COPYRIGHT_DOWN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_status_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resource_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    operator_id BIGINT UNSIGNED NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_resource_status_log (resource_id, created_at),
    KEY idx_resource_status_operator (operator_id),
    CONSTRAINT fk_resource_status_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT fk_resource_status_operator FOREIGN KEY (operator_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS download_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    attachment_id BIGINT UNSIGNED NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    download_ip VARCHAR(64) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    fail_reason VARCHAR(255) NULL,
    is_first_success TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_download_member_time (member_id, created_at),
    KEY idx_download_resource (resource_id),
    KEY idx_download_attachment (attachment_id),
    KEY idx_download_success (member_id, resource_id, status),
    CONSTRAINT fk_download_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT fk_download_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT fk_download_attachment FOREIGN KEY (attachment_id) REFERENCES file_attachment (id),
    CONSTRAINT ck_download_status CHECK (status IN ('SUCCESS', 'FAILED', 'DENIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_interaction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interaction (member_id, target_type, target_id, action_type),
    KEY idx_interaction_target (target_type, target_id, action_type, status),
    KEY idx_interaction_member (member_id, action_type, status),
    CONSTRAINT fk_interaction_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT ck_interaction_target CHECK (target_type IN ('RESOURCE', 'COMMENT', 'REQUEST_POST', 'REQUEST_REPLY')),
    CONSTRAINT ck_interaction_action CHECK (action_type IN ('FAVORITE', 'LIKE')),
    CONSTRAINT ck_interaction_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comment_info (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    root_id BIGINT UNSIGNED NULL,
    to_member_id BIGINT UNSIGNED NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_comment_target (target_type, target_id, status, created_at),
    KEY idx_comment_parent (parent_id),
    KEY idx_comment_member (member_id),
    KEY idx_comment_to_member (to_member_id),
    CONSTRAINT fk_comment_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment_info (id),
    CONSTRAINT fk_comment_root FOREIGN KEY (root_id) REFERENCES comment_info (id),
    CONSTRAINT fk_comment_to_member FOREIGN KEY (to_member_id) REFERENCES member_profile (id),
    CONSTRAINT ck_comment_target CHECK (target_type IN ('RESOURCE', 'REQUEST_POST')),
    CONSTRAINT ck_comment_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    CONSTRAINT ck_comment_content_length CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_rating (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    score TINYINT UNSIGNED NOT NULL,
    comment VARCHAR(300) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rating_member_resource (member_id, resource_id),
    KEY idx_rating_resource (resource_id),
    CONSTRAINT fk_rating_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT fk_rating_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT ck_rating_score CHECK (score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_post (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    requester_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NULL,
    title VARCHAR(80) NOT NULL,
    content VARCHAR(500) NOT NULL,
    expected_format VARCHAR(100) NULL,
    reward_points INT UNSIGNED NOT NULL DEFAULT 0,
    reward_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    status VARCHAR(30) NOT NULL DEFAULT 'ONGOING',
    accepted_reply_id BIGINT UNSIGNED NULL,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    answer_count INT UNSIGNED NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    deadline_time DATETIME(3) NULL,
    resolved_time DATETIME(3) NULL,
    closed_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_request_status_time (status, created_at),
    KEY idx_request_requester_status (requester_id, status),
    KEY idx_request_category_status (category_id, status),
    KEY idx_request_reward (status, reward_points),
    FULLTEXT KEY ft_request_search (title, content),
    CONSTRAINT fk_request_requester FOREIGN KEY (requester_id) REFERENCES member_profile (id),
    CONSTRAINT fk_request_category FOREIGN KEY (category_id) REFERENCES resource_category (id),
    CONSTRAINT ck_request_status CHECK (status IN ('ONGOING', 'RESOLVED', 'CANCELLED', 'CLOSED')),
    CONSTRAINT ck_request_title_length CHECK (CHAR_LENGTH(title) BETWEEN 5 AND 80),
    CONSTRAINT ck_request_content_length CHECK (CHAR_LENGTH(content) BETWEEN 20 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_tag_rel (
    request_id BIGINT UNSIGNED NOT NULL,
    tag_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (request_id, tag_id),
    UNIQUE KEY uk_request_tag (request_id, tag_id),
    KEY idx_request_tag_tag (tag_id),
    CONSTRAINT fk_request_tag_request FOREIGN KEY (request_id) REFERENCES request_post (id),
    CONSTRAINT fk_request_tag_tag FOREIGN KEY (tag_id) REFERENCES tag_info (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_reply (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL,
    replier_id BIGINT UNSIGNED NOT NULL,
    content VARCHAR(1000) NOT NULL,
    resource_id BIGINT UNSIGNED NULL,
    external_url VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    is_accepted TINYINT(1) NOT NULL DEFAULT 0,
    accepted_request_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN is_accepted = 1 THEN request_id ELSE NULL END) STORED,
    accepted_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reply_one_accepted (accepted_request_id),
    KEY idx_reply_request (request_id, status, created_at),
    KEY idx_reply_replier (replier_id),
    KEY idx_reply_accept (request_id, is_accepted),
    CONSTRAINT fk_reply_request FOREIGN KEY (request_id) REFERENCES request_post (id),
    CONSTRAINT fk_reply_replier FOREIGN KEY (replier_id) REFERENCES member_profile (id),
    CONSTRAINT fk_reply_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id),
    CONSTRAINT ck_reply_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    CONSTRAINT ck_reply_accepted CHECK (is_accepted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_status_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    operator_id BIGINT UNSIGNED NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_request_status_log (request_id, created_at),
    KEY idx_request_status_operator (operator_id),
    CONSTRAINT fk_request_status_request FOREIGN KEY (request_id) REFERENCES request_post (id),
    CONSTRAINT fk_request_status_operator FOREIGN KEY (operator_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS report_complaint (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT UNSIGNED NULL,
    report_type VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(120) NULL,
    reason VARCHAR(500) NOT NULL,
    proof_summary VARCHAR(1000) NULL,
    contact_email VARCHAR(120) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT UNSIGNED NULL,
    handle_result VARCHAR(500) NULL,
    handle_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_report_status_time (status, created_at),
    KEY idx_report_target (target_type, target_id),
    KEY idx_report_reporter (reporter_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member_profile (id),
    CONSTRAINT fk_report_handler FOREIGN KEY (handler_id) REFERENCES administrator_profile (id),
    CONSTRAINT ck_report_status CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_report_type CHECK (report_type IN ('RESOURCE', 'COMMENT', 'REQUEST_POST', 'REQUEST_REPLY', 'USER', 'COPYRIGHT')),
    CONSTRAINT ck_report_target CHECK (target_type IN ('RESOURCE', 'COMMENT', 'REQUEST_POST', 'REQUEST_REPLY', 'USER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS appeal_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appellant_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    related_report_id BIGINT UNSIGNED NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT UNSIGNED NULL,
    handle_result VARCHAR(500) NULL,
    handle_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_appeal_status_time (status, created_at),
    KEY idx_appeal_target (target_type, target_id),
    KEY idx_appeal_appellant (appellant_id),
    CONSTRAINT fk_appeal_appellant FOREIGN KEY (appellant_id) REFERENCES member_profile (id),
    CONSTRAINT fk_appeal_report FOREIGN KEY (related_report_id) REFERENCES report_complaint (id),
    CONSTRAINT fk_appeal_handler FOREIGN KEY (handler_id) REFERENCES administrator_profile (id),
    CONSTRAINT ck_appeal_status CHECK (status IN ('PENDING', 'PROCESSING', 'APPROVED', 'REJECTED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT UNSIGNED NOT NULL,
    receiver_id BIGINT UNSIGNED NULL,
    payload JSON NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    fail_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    process_time DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_event_status_time (status, created_at),
    KEY idx_event_source (source_type, source_id),
    KEY idx_event_receiver (receiver_id),
    CONSTRAINT fk_event_receiver FOREIGN KEY (receiver_id) REFERENCES member_profile (id),
    CONSTRAINT ck_event_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_notice (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id BIGINT UNSIGNED NULL,
    receiver_id BIGINT UNSIGNED NOT NULL,
    notice_type VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    target_type VARCHAR(50) NULL,
    target_id BIGINT UNSIGNED NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_notice_receiver_read (receiver_id, is_read, created_at),
    KEY idx_notice_type (notice_type),
    KEY idx_notice_target (target_type, target_id),
    CONSTRAINT fk_notice_event FOREIGN KEY (event_id) REFERENCES notification_event (id),
    CONSTRAINT fk_notice_receiver FOREIGN KEY (receiver_id) REFERENCES member_profile (id),
    CONSTRAINT ck_notice_read CHECK (is_read IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_id BIGINT UNSIGNED NULL,
    operation_type VARCHAR(60) NOT NULL,
    target_type VARCHAR(50) NULL,
    target_id BIGINT UNSIGNED NULL,
    content VARCHAR(1000) NULL,
    before_snapshot JSON NULL,
    after_snapshot JSON NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_admin_log_admin_time (admin_id, created_at),
    KEY idx_admin_log_target (target_type, target_id),
    CONSTRAINT fk_admin_log_admin FOREIGN KEY (admin_id) REFERENCES administrator_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT NOT NULL,
    value_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description VARCHAR(300) NULL,
    is_sensitive TINYINT(1) NOT NULL DEFAULT 0,
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key),
    KEY idx_config_enabled (is_enabled),
    CONSTRAINT ck_config_value_type CHECK (value_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_config_enabled CHECK (is_enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS platform_announcement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    publisher_id BIGINT UNSIGNED NULL,
    publish_time DATETIME(3) NULL,
    offline_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_announcement_status_time (status, publish_time),
    CONSTRAINT fk_announcement_publisher FOREIGN KEY (publisher_id) REFERENCES administrator_profile (id),
    CONSTRAINT ck_announcement_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_verification_code (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NULL,
    email VARCHAR(120) NOT NULL,
    scene VARCHAR(40) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNUSED',
    expire_time DATETIME(3) NOT NULL,
    used_time DATETIME(3) NULL,
    request_ip VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_email_scene_time (email, scene, created_at),
    KEY idx_email_status_expire (status, expire_time),
    CONSTRAINT fk_email_code_account FOREIGN KEY (account_id) REFERENCES user_account (id),
    CONSTRAINT ck_email_scene CHECK (scene IN ('REGISTER', 'RESET_PASSWORD', 'CHANGE_EMAIL', 'LOGIN')),
    CONSTRAINT ck_email_status CHECK (status IN ('UNUSED', 'USED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sensitive_word (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    word VARCHAR(100) NOT NULL,
    match_type VARCHAR(30) NOT NULL DEFAULT 'CONTAINS',
    severity VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sensitive_word (word),
    KEY idx_sensitive_status (status),
    CONSTRAINT ck_sensitive_match CHECK (match_type IN ('EXACT', 'CONTAINS', 'REGEX')),
    CONSTRAINT ck_sensitive_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_sensitive_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS search_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NULL,
    search_type VARCHAR(30) NOT NULL,
    keyword VARCHAR(120) NOT NULL,
    category_id BIGINT UNSIGNED NULL,
    result_count INT UNSIGNED NOT NULL DEFAULT 0,
    search_ip VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_search_member_time (member_id, created_at),
    KEY idx_search_keyword (keyword),
    KEY idx_search_type_time (search_type, created_at),
    CONSTRAINT fk_search_member FOREIGN KEY (member_id) REFERENCES member_profile (id),
    CONSTRAINT fk_search_category FOREIGN KEY (category_id) REFERENCES resource_category (id),
    CONSTRAINT ck_search_type CHECK (search_type IN ('RESOURCE', 'REQUEST', 'TAG'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resource_daily_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    download_count INT UNSIGNED NOT NULL DEFAULT 0,
    favorite_count INT UNSIGNED NOT NULL DEFAULT 0,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    rating_count INT UNSIGNED NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_daily (resource_id, stat_date),
    KEY idx_resource_daily_date (stat_date),
    CONSTRAINT fk_resource_daily_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member_daily_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    login_count INT UNSIGNED NOT NULL DEFAULT 0,
    publish_count INT UNSIGNED NOT NULL DEFAULT 0,
    download_count INT UNSIGNED NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    point_change INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_daily (member_id, stat_date),
    KEY idx_member_daily_date (stat_date),
    CONSTRAINT fk_member_daily_member FOREIGN KEY (member_id) REFERENCES member_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
