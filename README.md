# 资源分享论坛后端

本仓库用于开发“资源分享论坛系统”的 Spring Boot 后端。当前后端以开发设计说明书为接口和业务优先级，主接口前缀统一为 `/api/v1`，并保留 `/api/*` 兼容入口，方便现有前端逐步迁移。

后端已落地 V2 数据库、Flyway 迁移、JWT 鉴权、统一响应、分页结构和核心业务落库，覆盖账号认证、资源发布审核、附件下载、评论互动、资源评分、求资源悬赏、积分冻结结算、举报申诉和后台管理。

## 技术栈

- Java 21
- Spring Boot 3.3
- Spring Web / Validation / Security
- Spring JDBC / Flyway
- MySQL 8.x
- BCrypt / JWT
- Maven Wrapper

## 项目结构

```text
backend/
├── pom.xml
├── docker-compose.yml
├── README_DATABASE.md
├── src/main/java/com/resourcesharing/forum/
│   ├── controller/      # REST API 入口，含 /api/v1 与兼容路由
│   ├── service/         # 事务服务与数据库落库逻辑
│   ├── dto/             # 请求与响应 DTO
│   ├── domain/          # 领域枚举
│   ├── security/        # JWT 与认证过滤器
│   ├── config/          # 安全、追踪与框架配置
│   └── common/          # 统一响应、分页、异常
└── src/main/resources/
    ├── application.yml
    ├── schema.sql       # V2 手动建库脚本
    ├── data.sql         # V2 手动种子数据
    └── db/migration/    # Flyway 自动迁移脚本
```

## 数据库

数据库采用《资源分享论坛数据库设计说明书 V2.0》工程化模型，核心表共 35 张，使用 MySQL 8.x、`utf8mb4`、InnoDB 和 Flyway 管理版本。

核心表包括：

- `user_account`、`member_profile`、`administrator_profile`
- `membership_level`、`member_point_account`、`point_flow`
- `resource_info`、`resource_category`、`tag_info`、`file_attachment`
- `resource_audit_record`、`resource_status_log`
- `download_record`、`user_interaction`、`comment_info`、`resource_rating`
- `request_post`、`request_reply`、`request_status_log`
- `report_complaint`、`appeal_record`
- `notification_event`、`system_notice`、`admin_operation_log`、`system_config`

数据库文件：

- `backend/src/main/resources/schema.sql`：V2 手动建库脚本
- `backend/src/main/resources/data.sql`：V2 手动种子数据
- `backend/src/main/resources/db/migration/V1__create_v2_schema.sql`：Flyway 建表迁移
- `backend/src/main/resources/db/migration/V2__seed_v2_data.sql`：Flyway 种子数据迁移
- `backend/src/main/resources/db/migration/V3__fix_seed_account_password_hash.sql`：Flyway 种子账号 BCrypt 修复迁移
- `backend/README_DATABASE.md`：数据库导入、校验和运行说明

## 快速启动

```powershell
cd backend
```

创建 MySQL 数据库：

```sql
CREATE DATABASE resource_sharing_forum
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

配置数据库账号密码：

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "你的MySQL密码"
```

启动服务：

```powershell
.\mvnw.cmd spring-boot:run
```

默认服务地址：

```text
http://localhost:8080
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

## Docker MySQL

如果本机没有现成 MySQL，可以使用 `backend/docker-compose.yml` 启动 MySQL 8：

```powershell
cd backend
$env:MYSQL_ROOT_PASSWORD = "root"
docker compose up -d mysql
```

容器会自动创建 `resource_sharing_forum` 数据库，并使用 `utf8mb4_unicode_ci` 字符集排序规则。

## API 契约

主契约使用 `/api/v1`。兼容入口 `/api/auth`、`/api/resources`、`/api/demands`、`/api/requests` 等仍保留，但返回结构同样使用统一包装。

统一响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-06-04T23:47:12.000+08:00"
}
```

创建成功：

```json
{
  "code": 201,
  "message": "created",
  "data": {
    "id": 1
  },
  "timestamp": "2026-06-04T23:47:12.000+08:00"
}
```

分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "list": [],
    "page": 1,
    "size": 20
  },
  "timestamp": "2026-06-04T23:47:12.000+08:00"
}
```

分页查询参数统一为 `page` 和 `size`，默认 `page=1`、`size=20`，最大 `size=100`。

## API 模块

- `POST /api/v1/auth/register`：用户注册
- `POST /api/v1/auth/login`：用户或管理员登录
- `GET /api/v1/user/profile`：当前登录用户资料
- `PUT /api/v1/user/profile`：修改个人资料
- `POST /api/v1/user/profile/password`：修改密码
- `POST /api/v1/user/profile/email`：绑定或修改邮箱
- `GET /api/v1/resources`：资源列表和搜索
- `POST /api/v1/resources`：发布资源，默认进入 `PENDING_REVIEW`
- `GET /api/v1/resources/{id}`：资源详情
- `DELETE /api/v1/resources/{id}`：删除资源
- `POST /api/v1/resources/{id}/rating`：资源评分
- `PUT /api/v1/resources/{id}/audit`：资源审核
- `GET /api/v1/attachments/{id}/download`：附件下载
- `GET /api/v1/requests`：求资源列表
- `POST /api/v1/requests`：发布悬赏求资源，冻结积分
- `GET /api/v1/requests/{id}`：求资源详情
- `POST /api/v1/requests/{id}/cancel`：取消悬赏并退回冻结积分
- `GET /api/v1/requests/{id}/replies`：悬赏回复列表
- `POST /api/v1/requests/{id}/replies`：回复悬赏
- `POST /api/v1/requests/{id}/settle`：采纳回复并结算积分
- `GET /api/v1/comments`：评论列表
- `POST /api/v1/comments`：新增评论或回复
- `GET /api/v1/comments/{id}`：评论详情
- `PUT /api/v1/comments/{id}`：修改评论
- `DELETE /api/v1/comments/{id}`：删除评论
- `POST /api/v1/comments/{id}/like`：点赞或取消点赞评论
- `POST /api/v1/reports`：提交举报或版权投诉
- `POST /api/v1/appeals`：提交申诉
- `/api/v1/admin/**`：后台处理举报、申诉、会员、分类、标签、配置、日志和违规内容

## 测试

```powershell
cd backend
.\mvnw.cmd test
```

如果 Maven Central 访问受限，可以临时使用本地 Maven 镜像配置运行测试：

```powershell
.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test
```

该 settings 文件只用于本机测试，不需要提交到仓库。

## 种子账号

开发种子数据内置两个账号，密码均为 `password`：

- 普通会员：`demo_user`
- 管理员：`admin`

## 当前验证

- Maven 测试通过：`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`
- 冒烟测试覆盖：健康检查、登录、未授权拦截、JWT 个人资料、资源列表、求资源列表、评论列表、资源审核权限
- 数据库基础：Flyway V1/V2/V3 迁移提供 35 张 V2 表和可登录种子账号
