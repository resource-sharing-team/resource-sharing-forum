# SCHEMA.md

本文档维护资源分享论坛后端数据库结构，来源为 `src/main/resources/schema.sql` 与 Flyway 迁移脚本。
目标数据库为 MySQL 8.x，字符集为 `utf8mb4`。

## 通用约束

- 表名使用小写下划线命名。
- 主键统一使用 `id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT`。
- 时间字段统一使用 `created_at`、`updated_at`，软删除字段使用 `deleted_at`。
- 生产环境通过 Flyway 管理迁移，文件二进制不直接存入 MySQL。

## 表结构明细

### `user_account`

用户登录账号表，保存用户名、邮箱、密码哈希、角色、账号状态和登录安全信息。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `username` | `VARCHAR(50)` | 否 | `无` | 用户名 | 非空 |
| `email` | `VARCHAR(120)` | 否 | `无` | 邮箱地址 | 非空 |
| `password_hash` | `VARCHAR(255)` | 否 | `无` | 密码哈希值 | 非空 |
| `role` | `VARCHAR(30)` | 否 | `'USER'` | 账号角色 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'NORMAL'` | 业务状态 | 非空 |
| `failed_login_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `locked_until` | `DATETIME(3)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `last_login_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `last_login_ip` | `VARCHAR(64)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `password_changed_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_user_username (username)`
- `UNIQUE KEY uk_user_email (email)`
- `KEY idx_user_role (role)`
- `KEY idx_user_status (status)`
- `CONSTRAINT ck_user_role CHECK (role IN ('USER', 'AUDITOR', 'ADMIN', 'SUPER_ADMIN'))`
- `CONSTRAINT ck_user_status CHECK (status IN ('NORMAL', 'LOCKED', 'DISABLED', 'DELETED'))`

### `membership_level`

会员等级表，定义积分等级、下载限制、上传限制和悬赏限制。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `level_code` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `level_name` | `VARCHAR(50)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `min_points` | `INT UNSIGNED` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `max_points` | `INT UNSIGNED` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `daily_download_limit` | `INT UNSIGNED` | 否 | `10` | 业务字段，按接口和业务流程使用 | 非空 |
| `max_files_per_resource` | `INT UNSIGNED` | 否 | `5` | 业务字段，按接口和业务流程使用 | 非空 |
| `max_file_size_mb` | `INT UNSIGNED` | 否 | `100` | 业务字段，按接口和业务流程使用 | 非空 |
| `reward_limit` | `INT UNSIGNED` | 否 | `100` | 业务字段，按接口和业务流程使用 | 非空 |
| `can_apply_top` | `TINYINT(1)` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ENABLED'` | 业务状态 | 非空 |
| `sort_order` | `INT` | 否 | `0` | 排序值 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_level_code (level_code)`
- `KEY idx_level_points (min_points, max_points)`
- `KEY idx_level_status (status)`
- `CONSTRAINT ck_level_status CHECK (status IN ('ENABLED', 'DISABLED'))`

### `member_profile`

普通用户资料表，保存昵称、头像、学校专业和违规次数。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `account_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `nickname` | `VARCHAR(50)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `avatar_url` | `VARCHAR(500)` | 是 | `无` | 资源 URL | - |
| `gender` | `VARCHAR(20)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `bio` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `school` | `VARCHAR(100)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `major` | `VARCHAR(100)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `violation_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_member_account (account_id)`
- `KEY idx_member_nickname (nickname)`
- `CONSTRAINT fk_member_account FOREIGN KEY (account_id) REFERENCES user_account (id)`
- `CONSTRAINT ck_member_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'UNKNOWN'))`

### `administrator_profile`

管理员资料表，保存管理员身份、工号、权限组和状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `account_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `real_name` | `VARCHAR(50)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `employee_no` | `VARCHAR(50)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `permission_group` | `VARCHAR(60)` | 否 | `'ADMIN'` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ACTIVE'` | 业务状态 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_admin_account (account_id)`
- `UNIQUE KEY uk_admin_employee_no (employee_no)`
- `KEY idx_admin_group (permission_group)`
- `CONSTRAINT fk_admin_account FOREIGN KEY (account_id) REFERENCES user_account (id)`
- `CONSTRAINT ck_admin_status CHECK (status IN ('ACTIVE', 'DISABLED'))`

### `login_record`

登录记录表，审计用户登录结果、失败原因、IP 和客户端信息。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `account_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `login_account` | `VARCHAR(120)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `result` | `VARCHAR(30)` | 否 | `无` | 操作结果 | 非空 |
| `fail_reason` | `VARCHAR(255)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `login_ip` | `VARCHAR(64)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `user_agent` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_login_account_time (login_account, created_at)`
- `KEY idx_login_user_time (account_id, created_at)`
- `KEY idx_login_result (result)`
- `CONSTRAINT fk_login_account FOREIGN KEY (account_id) REFERENCES user_account (id)`
- `CONSTRAINT ck_login_result CHECK (result IN ('SUCCESS', 'FAILED', 'LOCKED', 'DISABLED'))`

### `member_point_account`

会员积分账户表，保存当前积分、冻结积分和累计积分。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `level_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `current_points` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `frozen_points` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `total_earned_points` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `total_spent_points` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_point_member (member_id)`
- `KEY idx_point_level (level_id)`
- `KEY idx_point_current (current_points)`
- `CONSTRAINT fk_point_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_point_level FOREIGN KEY (level_id) REFERENCES membership_level (id)`
- `CONSTRAINT ck_point_non_negative CHECK (current_points >= 0 AND frozen_points >= 0 AND frozen_points <= current_points)`

### `point_flow`

积分流水表，记录积分获取、冻结、扣减、退款和调整过程。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `flow_type` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `scene` | `VARCHAR(50)` | 否 | `无` | 业务场景 | 非空 |
| `points_change` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `frozen_change` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `before_points` | `INT` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `after_points` | `INT` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `before_frozen_points` | `INT` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `after_frozen_points` | `INT` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `related_type` | `VARCHAR(50)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `related_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `operator_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `description` | `VARCHAR(500)` | 是 | `无` | 详细描述 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_point_member_time (member_id, created_at)`
- `KEY idx_point_scene (scene)`
- `KEY idx_point_related (related_type, related_id)`
- `CONSTRAINT fk_flow_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_flow_operator FOREIGN KEY (operator_id) REFERENCES user_account (id)`
- `CONSTRAINT ck_flow_type CHECK (flow_type IN ('EARN', 'FREEZE', 'UNFREEZE', 'TRANSFER_IN', 'TRANSFER_OUT', 'DEDUCT', 'REFUND', 'ADJUST'))`
- `CONSTRAINT ck_flow_balance CHECK (after_points >= 0 AND after_frozen_points >= 0 AND after_frozen_points <= after_points)`

### `resource_category`

资源分类表，维护资源一级、二级分类树。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `parent_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `category_name` | `VARCHAR(60)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `level_no` | `TINYINT UNSIGNED` | 否 | `1` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ENABLED'` | 业务状态 | 非空 |
| `sort_order` | `INT` | 否 | `0` | 排序值 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_category_parent_name (parent_id, category_name)`
- `KEY idx_category_parent (parent_id)`
- `KEY idx_category_status (status)`
- `CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES resource_category (id)`
- `CONSTRAINT ck_category_level CHECK (level_no IN (1, 2))`
- `CONSTRAINT ck_category_status CHECK (status IN ('ENABLED', 'DISABLED'))`

### `tag_info`

标签表，保存资源标签名称、使用次数和启用状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `tag_name` | `VARCHAR(12)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `use_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ENABLED'` | 业务状态 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_tag_name (tag_name)`
- `KEY idx_tag_status (status)`
- `CONSTRAINT ck_tag_status CHECK (status IN ('ENABLED', 'DISABLED'))`

### `resource_info`

资源主表，保存资源发布信息、审核状态、统计计数和评分。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `publisher_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `category_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `title` | `VARCHAR(100)` | 否 | `无` | 标题 | 非空 |
| `resource_type` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `summary` | `VARCHAR(300)` | 否 | `无` | 摘要 | 非空 |
| `description` | `TEXT` | 否 | `无` | 详细描述 | 非空 |
| `external_url` | `VARCHAR(500)` | 是 | `无` | 资源 URL | - |
| `status` | `VARCHAR(30)` | 否 | `'PENDING_REVIEW'` | 业务状态 | 非空 |
| `reject_reason` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `view_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `download_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `favorite_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `like_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `comment_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `rating_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `average_rating` | `DECIMAL(3,2)` | 否 | `0.00` | 业务字段，按接口和业务流程使用 | 非空 |
| `current_version_no` | `INT UNSIGNED` | 否 | `1` | 业务字段，按接口和业务流程使用 | 非空 |
| `submitted_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `published_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `offline_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_resource_status_time (status, published_time)`
- `KEY idx_resource_category_status (category_id, status)`
- `KEY idx_resource_publisher_status (publisher_id, status)`
- `KEY idx_resource_type_status (resource_type, status)`
- `KEY idx_resource_hot (status, download_count, like_count, favorite_count)`
- `FULLTEXT KEY ft_resource_search (title, summary, description)`
- `CONSTRAINT fk_resource_publisher FOREIGN KEY (publisher_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_resource_category FOREIGN KEY (category_id) REFERENCES resource_category (id)`
- `CONSTRAINT ck_resource_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'REVIEWING_RISK', 'OFFLINE', 'COPYRIGHT_DOWN', 'DELETED'))`
- `CONSTRAINT ck_resource_type CHECK (resource_type IN ('DOCUMENT', 'SOFTWARE', 'SOURCE_CODE', 'MATERIAL', 'COURSE', 'TEMPLATE', 'LINK'))`
- `CONSTRAINT ck_resource_rating CHECK (average_rating >= 0 AND average_rating <= 5)`

### `resource_version`

资源版本表，保存资源每次提交审核时的内容快照。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `version_no` | `INT UNSIGNED` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `title` | `VARCHAR(100)` | 否 | `无` | 标题 | 非空 |
| `summary` | `VARCHAR(300)` | 否 | `无` | 摘要 | 非空 |
| `description` | `MEDIUMTEXT` | 否 | `无` | 详细描述 | 非空 |
| `category_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `resource_type` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `external_url` | `VARCHAR(500)` | 是 | `无` | 资源 URL | - |
| `tag_snapshot` | `JSON` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `attachment_snapshot` | `JSON` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `submitter_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `submit_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 业务时间 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_resource_version (resource_id, version_no)`
- `KEY idx_version_resource_time (resource_id, submit_time)`
- `CONSTRAINT fk_version_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT fk_version_category FOREIGN KEY (category_id) REFERENCES resource_category (id)`
- `CONSTRAINT fk_version_submitter FOREIGN KEY (submitter_id) REFERENCES member_profile (id)`

### `resource_tag_rel`

资源标签关联表，维护资源与标签的多对多关系。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `tag_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (resource_id, tag_id)`
- `UNIQUE KEY uk_resource_tag (resource_id, tag_id)`
- `KEY idx_resource_tag_tag (tag_id)`
- `CONSTRAINT fk_resource_tag_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT fk_resource_tag_tag FOREIGN KEY (tag_id) REFERENCES tag_info (id)`

### `file_attachment`

文件附件表，保存附件元数据和服务器存储路径。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `owner_type` | `VARCHAR(40)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `owner_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `uploader_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `original_file_name` | `VARCHAR(255)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `stored_file_name` | `VARCHAR(255)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `file_ext` | `VARCHAR(20)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `mime_type` | `VARCHAR(120)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `file_size` | `BIGINT UNSIGNED` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `file_hash` | `VARCHAR(128)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `storage_path` | `VARCHAR(700)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'TEMP'` | 业务状态 | 非空 |
| `download_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_attachment_owner (owner_type, owner_id)`
- `KEY idx_attachment_uploader (uploader_id)`
- `KEY idx_attachment_status (status)`
- `KEY idx_attachment_hash (file_hash)`
- `CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_id) REFERENCES user_account (id)`
- `CONSTRAINT ck_attachment_owner CHECK (owner_type IN ('RESOURCE', 'REQUEST_POST', 'REQUEST_REPLY', 'REPORT_COMPLAINT', 'APPEAL'))`
- `CONSTRAINT ck_attachment_status CHECK (status IN ('TEMP', 'NORMAL', 'DELETED', 'BLOCKED'))`

### `resource_audit_record`

资源审核记录表，记录审核动作、意见、风险等级和审核人。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `version_no` | `INT UNSIGNED` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `auditor_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `audit_result` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `reason` | `VARCHAR(500)` | 是 | `无` | 原因说明 | - |
| `audit_time` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 业务时间 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_audit_resource (resource_id)`
- `KEY idx_audit_auditor_time (auditor_id, audit_time)`
- `CONSTRAINT fk_audit_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT fk_audit_auditor FOREIGN KEY (auditor_id) REFERENCES administrator_profile (id)`
- `CONSTRAINT ck_audit_result CHECK (audit_result IN ('APPROVED', 'REJECTED', 'OFFLINE', 'RESTORED', 'RISK_REVIEW', 'COPYRIGHT_DOWN', 'DELETED'))`

### `resource_status_log`

资源状态日志表，记录资源状态流转和操作原因。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `from_status` | `VARCHAR(30)` | 是 | `无` | 业务状态 | - |
| `to_status` | `VARCHAR(30)` | 否 | `无` | 业务状态 | 非空 |
| `operator_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `reason` | `VARCHAR(500)` | 是 | `无` | 原因说明 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_resource_status_log (resource_id, created_at)`
- `KEY idx_resource_status_operator (operator_id)`
- `CONSTRAINT fk_resource_status_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT fk_resource_status_operator FOREIGN KEY (operator_id) REFERENCES user_account (id)`

### `download_record`

下载记录表，记录用户下载附件和积分消耗情况。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `attachment_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `file_name` | `VARCHAR(255)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `download_ip` | `VARCHAR(64)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `status` | `VARCHAR(30)` | 否 | `'SUCCESS'` | 业务状态 | 非空 |
| `fail_reason` | `VARCHAR(255)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `is_first_success` | `TINYINT(1)` | 否 | `0` | 布尔标记 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_download_member_time (member_id, created_at)`
- `KEY idx_download_resource (resource_id)`
- `KEY idx_download_attachment (attachment_id)`
- `KEY idx_download_success (member_id, resource_id, status)`
- `CONSTRAINT fk_download_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_download_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT fk_download_attachment FOREIGN KEY (attachment_id) REFERENCES file_attachment (id)`
- `CONSTRAINT ck_download_status CHECK (status IN ('SUCCESS', 'FAILED', 'DENIED'))`

### `user_interaction`

用户互动表，统一保存收藏、点赞、评论点赞等互动关系。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `target_type` | `VARCHAR(40)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `target_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `action_type` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ACTIVE'` | 业务状态 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_interaction (member_id, target_type, target_id, action_type)`
- `KEY idx_interaction_target (target_type, target_id, action_type, status)`
- `KEY idx_interaction_member (member_id, action_type, status)`
- `CONSTRAINT fk_interaction_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT ck_interaction_target CHECK (target_type IN ('RESOURCE', 'COMMENT', 'REQUEST_POST', 'REQUEST_REPLY'))`
- `CONSTRAINT ck_interaction_action CHECK (action_type IN ('FAVORITE', 'LIKE'))`
- `CONSTRAINT ck_interaction_status CHECK (status IN ('ACTIVE', 'CANCELLED'))`

### `comment_info`

评论表，保存资源评论、父评论、点赞数和审核状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `target_type` | `VARCHAR(40)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `target_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `parent_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `root_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `to_member_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `content` | `VARCHAR(500)` | 否 | `无` | 正文内容 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ACTIVE'` | 业务状态 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_comment_target (target_type, target_id, status, created_at)`
- `KEY idx_comment_parent (parent_id)`
- `KEY idx_comment_member (member_id)`
- `KEY idx_comment_to_member (to_member_id)`
- `CONSTRAINT fk_comment_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment_info (id)`
- `CONSTRAINT fk_comment_root FOREIGN KEY (root_id) REFERENCES comment_info (id)`
- `CONSTRAINT fk_comment_to_member FOREIGN KEY (to_member_id) REFERENCES member_profile (id)`
- `CONSTRAINT ck_comment_target CHECK (target_type IN ('RESOURCE', 'REQUEST_POST'))`
- `CONSTRAINT ck_comment_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))`
- `CONSTRAINT ck_comment_content_length CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 500)`

### `resource_rating`

资源评分表，保存用户对资源的评分。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `score` | `TINYINT UNSIGNED` | 否 | `无` | 评分值 | 非空 |
| `comment` | `VARCHAR(300)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_rating_member_resource (member_id, resource_id)`
- `KEY idx_rating_resource (resource_id)`
- `CONSTRAINT fk_rating_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_rating_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT ck_rating_score CHECK (score BETWEEN 1 AND 5)`

### `request_post`

求资源帖子表，保存求资源需求、悬赏积分和状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `requester_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `category_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `title` | `VARCHAR(80)` | 否 | `无` | 标题 | 非空 |
| `content` | `VARCHAR(500)` | 否 | `无` | 正文内容 | 非空 |
| `expected_format` | `VARCHAR(100)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `reward_points` | `INT UNSIGNED` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ONGOING'` | 业务状态 | 非空 |
| `accepted_reply_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `view_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `answer_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `comment_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `deadline_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `resolved_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `closed_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_request_status_time (status, created_at)`
- `KEY idx_request_requester_status (requester_id, status)`
- `KEY idx_request_category_status (category_id, status)`
- `KEY idx_request_reward (status, reward_points)`
- `FULLTEXT KEY ft_request_search (title, content)`
- `CONSTRAINT fk_request_requester FOREIGN KEY (requester_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_request_category FOREIGN KEY (category_id) REFERENCES resource_category (id)`
- `CONSTRAINT ck_request_status CHECK (status IN ('ONGOING', 'RESOLVED', 'CANCELLED', 'CLOSED'))`
- `CONSTRAINT ck_request_title_length CHECK (CHAR_LENGTH(title) BETWEEN 5 AND 80)`
- `CONSTRAINT ck_request_content_length CHECK (CHAR_LENGTH(content) BETWEEN 20 AND 500)`

### `request_tag_rel`

求资源标签关联表。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `request_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `tag_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (request_id, tag_id)`
- `UNIQUE KEY uk_request_tag (request_id, tag_id)`
- `KEY idx_request_tag_tag (tag_id)`
- `CONSTRAINT fk_request_tag_request FOREIGN KEY (request_id) REFERENCES request_post (id)`
- `CONSTRAINT fk_request_tag_tag FOREIGN KEY (tag_id) REFERENCES tag_info (id)`

### `request_reply`

求资源回答表，保存回答内容、关联资源和采纳状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `request_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `replier_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `content` | `VARCHAR(1000)` | 否 | `无` | 正文内容 | 非空 |
| `resource_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `external_url` | `VARCHAR(500)` | 是 | `无` | 资源 URL | - |
| `status` | `VARCHAR(30)` | 否 | `'ACTIVE'` | 业务状态 | 非空 |
| `is_accepted` | `TINYINT(1)` | 否 | `0` | 布尔标记 | 非空 |
| `accepted_request_id` | `BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN is_accepted = 1 THEN request_id ELSE` | 是 | `无` | 关联业务对象 ID | - |
| `accepted_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_reply_one_accepted (accepted_request_id)`
- `KEY idx_reply_request (request_id, status, created_at)`
- `KEY idx_reply_replier (replier_id)`
- `KEY idx_reply_accept (request_id, is_accepted)`
- `CONSTRAINT fk_reply_request FOREIGN KEY (request_id) REFERENCES request_post (id)`
- `CONSTRAINT fk_reply_replier FOREIGN KEY (replier_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_reply_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`
- `CONSTRAINT ck_reply_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED'))`
- `CONSTRAINT ck_reply_accepted CHECK (is_accepted IN (0, 1))`

### `request_status_log`

求资源状态日志表，记录需求状态变化和操作人。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `request_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `from_status` | `VARCHAR(30)` | 是 | `无` | 业务状态 | - |
| `to_status` | `VARCHAR(30)` | 否 | `无` | 业务状态 | 非空 |
| `operator_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `reason` | `VARCHAR(500)` | 是 | `无` | 原因说明 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_request_status_log (request_id, created_at)`
- `KEY idx_request_status_operator (operator_id)`
- `CONSTRAINT fk_request_status_request FOREIGN KEY (request_id) REFERENCES request_post (id)`
- `CONSTRAINT fk_request_status_operator FOREIGN KEY (operator_id) REFERENCES user_account (id)`

### `report_complaint`

举报投诉表，保存举报、版权投诉和处理结果。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `reporter_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `report_type` | `VARCHAR(40)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `target_type` | `VARCHAR(40)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `target_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `title` | `VARCHAR(120)` | 是 | `无` | 标题 | - |
| `reason` | `VARCHAR(500)` | 否 | `无` | 原因说明 | 非空 |
| `proof_summary` | `VARCHAR(1000)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `contact_email` | `VARCHAR(120)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `status` | `VARCHAR(30)` | 否 | `'PENDING'` | 业务状态 | 非空 |
| `handler_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `handle_result` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `handle_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_report_status_time (status, created_at)`
- `KEY idx_report_target (target_type, target_id)`
- `KEY idx_report_reporter (reporter_id)`
- `CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_report_handler FOREIGN KEY (handler_id) REFERENCES administrator_profile (id)`
- `CONSTRAINT ck_report_status CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED'))`
- `CONSTRAINT ck_report_type CHECK (report_type IN ('RESOURCE', 'COMMENT', 'REQUEST_POST', 'REQUEST_REPLY', 'USER', 'COPYRIGHT'))`
- `CONSTRAINT ck_report_target CHECK (target_type IN ('RESOURCE', 'COMMENT', 'REQUEST_POST', 'REQUEST_REPLY', 'USER'))`

### `appeal_record`

申诉记录表，保存用户申诉理由、材料和处理结果。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `appellant_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `target_type` | `VARCHAR(40)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `target_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `related_report_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `reason` | `VARCHAR(1000)` | 否 | `无` | 原因说明 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'PENDING'` | 业务状态 | 非空 |
| `handler_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `handle_result` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `handle_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_appeal_status_time (status, created_at)`
- `KEY idx_appeal_target (target_type, target_id)`
- `KEY idx_appeal_appellant (appellant_id)`
- `CONSTRAINT fk_appeal_appellant FOREIGN KEY (appellant_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_appeal_report FOREIGN KEY (related_report_id) REFERENCES report_complaint (id)`
- `CONSTRAINT fk_appeal_handler FOREIGN KEY (handler_id) REFERENCES administrator_profile (id)`
- `CONSTRAINT ck_appeal_status CHECK (status IN ('PENDING', 'PROCESSING', 'APPROVED', 'REJECTED', 'CANCELLED'))`

### `notification_event`

通知事件表，保存系统生成通知的来源事件。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `event_type` | `VARCHAR(50)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `source_type` | `VARCHAR(50)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `source_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `receiver_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `payload` | `JSON` | 是 | `无` | JSON 事件载荷 | - |
| `status` | `VARCHAR(30)` | 否 | `'PENDING'` | 业务状态 | 非空 |
| `fail_reason` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `process_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_event_status_time (status, created_at)`
- `KEY idx_event_source (source_type, source_id)`
- `KEY idx_event_receiver (receiver_id)`
- `CONSTRAINT fk_event_receiver FOREIGN KEY (receiver_id) REFERENCES member_profile (id)`
- `CONSTRAINT ck_event_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'IGNORED'))`

### `system_notice`

用户通知表，保存接收人、读取状态和发送状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `event_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `receiver_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `notice_type` | `VARCHAR(50)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `title` | `VARCHAR(120)` | 否 | `无` | 标题 | 非空 |
| `content` | `VARCHAR(1000)` | 否 | `无` | 正文内容 | 非空 |
| `target_type` | `VARCHAR(50)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `target_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `is_read` | `TINYINT(1)` | 否 | `0` | 布尔标记 | 非空 |
| `read_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_notice_receiver_read (receiver_id, is_read, created_at)`
- `KEY idx_notice_type (notice_type)`
- `KEY idx_notice_target (target_type, target_id)`
- `CONSTRAINT fk_notice_event FOREIGN KEY (event_id) REFERENCES notification_event (id)`
- `CONSTRAINT fk_notice_receiver FOREIGN KEY (receiver_id) REFERENCES member_profile (id)`
- `CONSTRAINT ck_notice_read CHECK (is_read IN (0, 1))`

### `admin_operation_log`

后台操作日志表，审计管理员操作。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `admin_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `operation_type` | `VARCHAR(60)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `target_type` | `VARCHAR(50)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `target_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `content` | `VARCHAR(1000)` | 是 | `无` | 正文内容 | - |
| `before_snapshot` | `JSON` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `after_snapshot` | `JSON` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `ip` | `VARCHAR(64)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `user_agent` | `VARCHAR(500)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_admin_log_admin_time (admin_id, created_at)`
- `KEY idx_admin_log_target (target_type, target_id)`
- `CONSTRAINT fk_admin_log_admin FOREIGN KEY (admin_id) REFERENCES administrator_profile (id)`

### `system_config`

系统配置表，保存运行参数和配置类型。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `config_key` | `VARCHAR(100)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `config_value` | `TEXT` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `value_type` | `VARCHAR(20)` | 否 | `'STRING'` | 业务字段，按接口和业务流程使用 | 非空 |
| `description` | `VARCHAR(300)` | 是 | `无` | 详细描述 | - |
| `is_sensitive` | `TINYINT(1)` | 否 | `0` | 布尔标记 | 非空 |
| `is_enabled` | `TINYINT(1)` | 否 | `1` | 布尔标记 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_config_key (config_key)`
- `KEY idx_config_enabled (is_enabled)`
- `CONSTRAINT ck_config_value_type CHECK (value_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'JSON'))`
- `CONSTRAINT ck_config_enabled CHECK (is_enabled IN (0, 1))`

### `platform_announcement`

平台公告表，保存公告内容、发布范围和有效期。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `title` | `VARCHAR(120)` | 否 | `无` | 标题 | 非空 |
| `content` | `TEXT` | 否 | `无` | 正文内容 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'DRAFT'` | 业务状态 | 非空 |
| `publisher_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `publish_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `offline_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_announcement_status_time (status, publish_time)`
- `CONSTRAINT fk_announcement_publisher FOREIGN KEY (publisher_id) REFERENCES administrator_profile (id)`
- `CONSTRAINT ck_announcement_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'OFFLINE'))`

### `email_verification_code`

邮箱验证码表，保存验证码哈希、过期时间和使用状态。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `account_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `email` | `VARCHAR(120)` | 否 | `无` | 邮箱地址 | 非空 |
| `scene` | `VARCHAR(40)` | 否 | `无` | 业务场景 | 非空 |
| `code_hash` | `VARCHAR(255)` | 否 | `无` | 验证码哈希值 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'UNUSED'` | 业务状态 | 非空 |
| `expire_time` | `DATETIME(3)` | 否 | `无` | 业务时间 | 非空 |
| `used_time` | `DATETIME(3)` | 是 | `无` | 业务时间 | - |
| `request_ip` | `VARCHAR(64)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_email_scene_time (email, scene, created_at)`
- `KEY idx_email_status_expire (status, expire_time)`
- `CONSTRAINT fk_email_code_account FOREIGN KEY (account_id) REFERENCES user_account (id)`
- `CONSTRAINT ck_email_scene CHECK (scene IN ('REGISTER', 'RESET_PASSWORD', 'CHANGE_EMAIL', 'LOGIN'))`
- `CONSTRAINT ck_email_status CHECK (status IN ('UNUSED', 'USED', 'EXPIRED'))`

### `sensitive_word`

敏感词表，保存内容审核词库和严重程度。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `word` | `VARCHAR(100)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `match_type` | `VARCHAR(30)` | 否 | `'CONTAINS'` | 业务字段，按接口和业务流程使用 | 非空 |
| `severity` | `VARCHAR(30)` | 否 | `'MEDIUM'` | 业务字段，按接口和业务流程使用 | 非空 |
| `status` | `VARCHAR(30)` | 否 | `'ENABLED'` | 业务状态 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |
| `deleted_at` | `DATETIME(3)` | 是 | `无` | 软删除时间，非空表示逻辑删除 | 软删除字段 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_sensitive_word (word)`
- `KEY idx_sensitive_status (status)`
- `CONSTRAINT ck_sensitive_match CHECK (match_type IN ('EXACT', 'CONTAINS', 'REGEX'))`
- `CONSTRAINT ck_sensitive_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH'))`
- `CONSTRAINT ck_sensitive_status CHECK (status IN ('ENABLED', 'DISABLED'))`

### `search_log`

搜索日志表，保存搜索关键词和结果数量。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `member_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `search_type` | `VARCHAR(30)` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `keyword` | `VARCHAR(120)` | 否 | `无` | 搜索关键词 | 非空 |
| `category_id` | `BIGINT UNSIGNED` | 是 | `无` | 关联业务对象 ID | - |
| `result_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `search_ip` | `VARCHAR(64)` | 是 | `无` | 业务字段，按接口和业务流程使用 | - |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `KEY idx_search_member_time (member_id, created_at)`
- `KEY idx_search_keyword (keyword)`
- `KEY idx_search_type_time (search_type, created_at)`
- `CONSTRAINT fk_search_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
- `CONSTRAINT fk_search_category FOREIGN KEY (category_id) REFERENCES resource_category (id)`
- `CONSTRAINT ck_search_type CHECK (search_type IN ('RESOURCE', 'REQUEST', 'TAG'))`

### `resource_daily_stat`

资源日统计表，保存资源每日访问和互动增量。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `stat_date` | `DATE` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `resource_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `view_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `download_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `favorite_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `like_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `rating_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `comment_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_resource_daily (resource_id, stat_date)`
- `KEY idx_resource_daily_date (stat_date)`
- `CONSTRAINT fk_resource_daily_resource FOREIGN KEY (resource_id) REFERENCES resource_info (id)`

### `member_daily_stat`

用户日统计表，保存用户每日发布、下载、积分和互动统计。

| 字段 | 类型 | 可空 | 默认值 | 含义 | 约束说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 否 | `无` | 主键，自增唯一标识 | 主键；自增；非空 |
| `stat_date` | `DATE` | 否 | `无` | 业务字段，按接口和业务流程使用 | 非空 |
| `member_id` | `BIGINT UNSIGNED` | 否 | `无` | 关联业务对象 ID | 非空 |
| `login_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `publish_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `download_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `comment_count` | `INT UNSIGNED` | 否 | `0` | 业务计数 | 非空 |
| `point_change` | `INT` | 否 | `0` | 业务字段，按接口和业务流程使用 | 非空 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间，统一时间字段命名 | 非空 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间，记录最近一次数据变更 | 非空 |

约束与索引：

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_member_daily (member_id, stat_date)`
- `KEY idx_member_daily_date (stat_date)`
- `CONSTRAINT fk_member_daily_member FOREIGN KEY (member_id) REFERENCES member_profile (id)`
