# 前端接口联调状态汇总

> 范围：当前 `Web_Admin` 分支已合入最新 `origin/main` 后的前端与后端接口状态。
> 约定：前端通过 `VITE_API_BASE_URL`、`VITE_API_PREFIX=/api` 切换真实后端；后端同时保留 `/api` 与 `/api/v1` 兼容路径，统一响应 `{ code, message, data, timestamp }`，分页 `{ total, list, page, size }`。

## 已完成

### 用户端 `web_user`

| 模块 | 用户端调用 | 后端接口 | 状态 |
| --- | --- | --- | --- |
| 登录 | `POST /auth/login` | `POST /api/auth/login`、`POST /api/v1/auth/login` | 已实现 |
| 注册提交 | `POST /auth/register` | `POST /api/auth/register`、`POST /api/v1/auth/register` | 前后端均有实现，但前端未传邮箱验证码 |
| 找回密码提交 | `POST /auth/reset-password` | `POST /api/auth/reset-password`、`POST /api/v1/auth/reset-password` | 已实现 |
| 当前用户资料 | `GET /me`、`PUT /me` | `GET/PUT /api/me`、`GET/PUT /api/v1/user/profile` | 已实现 |
| 修改密码/绑定邮箱/个人汇总 | `POST /me/password`、`POST /me/email`、`GET /me/summary` | 后端对应路径已实现 | 已实现 |
| 资源列表/详情 | `GET /resources`、`GET /resources/{id}` | `GET /api/resources`、`GET /api/resources/{id}` | 已实现 |
| 发布资源 | `POST /resources` | 支持 `multipart/form-data` 与 JSON | 已实现 |
| 资源互动 | `POST /resources/{id}/like`、`favorite`、`download`、`rating` | 后端对应路径已实现 | 已实现 |
| 资源评论 | `POST /resources/{id}/comments` | 后端对应路径已实现 | 已实现 |
| 求资源列表/详情 | `GET /demands`、`GET /demands/{id}` | `GET /api/demands`，并兼容 `/api/requests` | 已实现 |
| 发布求资源/回复 | `POST /demands`、`POST /demands/{id}/replies` | 后端对应路径已实现 | 已实现 |
| 举报 | `POST /reports` | `POST /api/reports`、`POST /api/v1/reports` | 已实现 |
| 分类、标签、资源类型、公告 | 后端提供 `/api/categories`、`/api/tags/suggest`、`/api/resource-types`、`/api/announcements` | 后端已实现 | 用户端目前主要仍使用本地目录数据，后续可切到真实接口 |

### 管理端 `web_admin`

| 模块 | 管理端调用 | 后端接口 | 状态 |
| --- | --- | --- | --- |
| 管理员登录 | `POST /auth/login` | `POST /api/auth/login` | 已封装，真实数据库需返回 `ADMIN` 角色 |
| 内容综合管理 | `GET /admin/content` | `GET /api/admin/content`、`GET /api/v1/admin/content` | 已实现 |
| 资源审核/上下架 | `POST /admin/resources/{id}/approve`、`reject`、`offline`、`restore`、`copyright-down`、`DELETE /admin/resources/{id}` | 后端对应路径已实现 | 已实现 |
| 评论管理 | `POST /admin/comments/{id}/hide`、`restore`、`DELETE /admin/comments/{id}` | 后端对应路径已实现 | 已实现 |
| 用户账号管理 | `GET /admin/users`、`PUT /admin/members/{id}/disable`、`enable` | 后端对应路径已实现 | 已实现 |
| 举报/申诉处理 | `GET /admin/compliance`、`POST /admin/reports/{id}/handle`、`POST /admin/appeals/{id}/handle` | 后端对应路径已实现 | 已实现 |
| 求资源管理 | `POST /admin/requests/{id}/close`、`DELETE /admin/replies/{id}` | 后端对应路径已实现 | 已实现 |
| 分类标签管理 | `GET /admin/catalog`、`/catalog/options`、`POST/PUT /admin/categories`、`POST/PUT /admin/tags`、`/tags/backfill`、`/tags/merge` | 后端对应路径已实现 | 已实现 |
| 系统参数配置 | `GET /admin/config/full`、`PUT /admin/config`、`PUT /admin/config/member-levels/{id}`、`POST /admin/cache/refresh` | 后端对应路径已实现 | 已实现 |
| 操作审计日志 | `GET /admin/logs` | `GET /api/admin/logs`、`GET /api/v1/admin/logs` | 已实现 |

## 需修改 / 需注意

| 位置 | 问题 | 建议 |
| --- | --- | --- |
| `web_user` 注册页 | 后端注册要求邮箱验证码，前端当前没有调用 `/auth/register/code`，也没有给注册提交传 `code` | 用户端补“发送验证码”按钮、验证码输入框，并把 `code` 传给 `register` |
| `web_user` 找回密码页 | 前端有验证码输入框，但没有调用 `/auth/reset-password/code` 发送验证码 | 用户端补“发送验证码”按钮 |
| `web_user` 分类/标签/公告 | 后端已提供公开元数据接口，但用户端仍主要使用本地数据 | 后续联调时可将本地 `catalog` 切到真实接口 |
| 管理端无数据库 smoke 联调 | 后端 no-database 模式下 `/auth/login` 返回 `MEMBER`，管理端会正确拒绝进入后台 | 若希望无数据库也能验管理端，需要后端提供测试用 `ADMIN` fallback，或使用真实 MySQL 管理员账号联调 |

## 未实现

- 管理端必需接口未发现缺口：页面需要的列表、审核、上下架、举报申诉、用户禁用恢复、分类标签、系统配置、日志查询接口均已有前后端对应实现。
- 用户端核心浏览、发布、互动、个人中心接口已基本具备；主要缺口集中在邮箱验证码发送流程的前端页面接入。

## 验证记录

- 管理端 API 封装与页面测试：`npm.cmd run test` 通过，4 个测试文件，17 个用例。
- 管理端生产构建：`npm.cmd run build` 通过。
- 后端测试：`.\mvnw.cmd test` 通过，123 个测试，0 failures，0 errors，9 skipped；跳过项为本机无 Docker 的 Testcontainers/MySQL 类测试。
- 编码规范自查：`web_admin/src` 无 `console.log/debugger`；`git diff --check` 无行尾空格错误；`web_admin/src` 超过 100 字符长行已清理为 0。
- 真实后端 smoke：后端以无数据库配置启动，`GET http://127.0.0.1:18080/api/health` 返回 `database="UNCONFIGURED"`；管理端真实后端模式登录能请求到后端，并因返回 `MEMBER` 角色显示“当前账号不是管理员，无法进入后台”。
