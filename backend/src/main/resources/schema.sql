CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME,
    last_login_at DATETIME,
    last_login_ip VARCHAR(64),
    member_level_id BIGINT,
    points INT NOT NULL DEFAULT 0,
    frozen_points INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS member_levels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    level_name VARCHAR(50) NOT NULL,
    min_points INT NOT NULL,
    daily_download_limit INT NOT NULL,
    max_files_per_resource INT NOT NULL,
    max_file_size_mb INT NOT NULL,
    reward_limit INT NOT NULL,
    can_apply_top TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    use_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publisher_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    category_id BIGINT NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    external_url VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    reject_reason VARCHAR(500),
    view_count INT NOT NULL DEFAULT 0,
    download_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    score DECIMAL(3, 2) NOT NULL DEFAULT 0,
    published_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resource_status (status),
    INDEX idx_resource_category (category_id),
    INDEX idx_resource_publisher (publisher_id)
);

CREATE TABLE IF NOT EXISTS resource_tags (
    resource_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (resource_id, tag_id)
);

CREATE TABLE IF NOT EXISTS attachments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT,
    uploader_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    access_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TEMP',
    download_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attachment_resource (resource_id)
);

CREATE TABLE IF NOT EXISTS audit_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    auditor_id BIGINT,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_target (target_type, target_id)
);

CREATE TABLE IF NOT EXISTS download_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    download_ip VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    downloaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_download_user_time (user_id, downloaded_at)
);

CREATE TABLE IF NOT EXISTS favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_favorite_user_resource (user_id, resource_id)
);

CREATE TABLE IF NOT EXISTS likes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_like_target (user_id, target_type, target_id)
);

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_comment_resource (resource_id)
);

CREATE TABLE IF NOT EXISTS resource_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category_id BIGINT,
    tags VARCHAR(255),
    expected_format VARCHAR(100),
    reward_type VARCHAR(20) NOT NULL DEFAULT 'FREE',
    reward_points INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    accepted_answer_id BIGINT,
    deadline_at DATETIME,
    view_count INT NOT NULL DEFAULT 0,
    answer_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS request_answers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    answerer_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    resource_id BIGINT,
    external_url VARCHAR(500),
    attachment_file_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    is_accepted TINYINT NOT NULL DEFAULT 0,
    accepted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_answer_request (request_id)
);

CREATE TABLE IF NOT EXISTS point_flows (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    change_value INT NOT NULL DEFAULT 0,
    frozen_change INT NOT NULL DEFAULT 0,
    before_points INT NOT NULL,
    after_points INT NOT NULL,
    before_frozen_points INT NOT NULL,
    after_frozen_points INT NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    related_type VARCHAR(30),
    related_id BIGINT,
    description VARCHAR(300),
    operator_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_point_user_time (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    report_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT,
    handle_result VARCHAR(500),
    handled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS copyright_complaints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    complainant_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    proof_material VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT,
    handle_result VARCHAR(500),
    handled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500) NOT NULL,
    related_type VARCHAR(30),
    related_id BIGINT,
    read_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notice_receiver (receiver_id, read_status)
);

CREATE TABLE IF NOT EXISTS operation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT,
    operation_type VARCHAR(40) NOT NULL,
    target_type VARCHAR(30),
    target_id BIGINT,
    content VARCHAR(500),
    ip VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_operator_time (operator_id, created_at)
);

