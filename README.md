# 资源分享论坛项目

这是“资源分享论坛”课程项目仓库。当前联调结构把规范化 Spring Boot 后端、用户端前端和管理端前端放在同一个仓库中，方便按统一接口契约进行开发、测试和部署。

## 目录结构

```text
backend/      Spring Boot 后端，提供 /api 与 /api/v1 兼容接口
web_user/     用户端 React + Vite 前端
web_admin/    管理端 React + Vite 前端
```

## 后端

后端代码位于 `backend/`，已按设计说明书推进 V2 数据库、Flyway 迁移、JWT 鉴权、统一响应、分页结构和核心业务规则落地。

启动后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

测试：

```powershell
cd backend
.\mvnw.cmd -s D:\tmp\maven-aliyun-settings.xml test
```

## 用户端前端

用户端代码位于 `web_user/`，默认可使用 MSW mock，也可以通过环境变量直连后端。

启动用户端：

```powershell
cd web_user
npm install
npm run dev
```

真实后端联调环境变量：

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_API_PREFIX=/api
VITE_ENABLE_MOCKS=false
```

## 管理端前端

管理端代码位于 `web_admin/`，当前已覆盖后台登录、内容管理、用户管理、举报投诉、分类标签、系统配置和操作日志页面。

启动管理端：

```powershell
cd web_admin
npm install
npm run dev
```

管理端后续优先对接后端 `/api/admin/**` 接口。

## 接口契约

主接口前缀优先使用 `/api/v1`，同时保留 `/api/*` 兼容入口，便于现有前端渐进迁移。

统一响应结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-06-04T23:47:12.000+08:00"
}
```

分页结构：

```json
{
  "total": 100,
  "list": [],
  "page": 1,
  "size": 20
}
```

前端 Axios 层会兼容统一响应，并把分页字段转换为页面使用的 `items` 和 `pageSize`。

## 开发注意

- 不要删除 `backend/`、`web_user/`、`web_admin/` 任一目录，避免跨分支合并时误删代码。
- `node_modules/`、`dist/`、日志文件和本地 `.env` 不提交到 Git。
- 后端跨域配置需要包含前端开发地址，例如 `http://localhost:5173,http://127.0.0.1:5173`。
- 用户端和管理端都保留 mock 能力，真实联调时通过 `VITE_API_BASE_URL` 与 `VITE_ENABLE_MOCKS=false` 切换到后端。
