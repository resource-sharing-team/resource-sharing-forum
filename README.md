# 资源分享论坛后端

本仓库用于搭建“资源分享论坛”的后端开发环境。首版聚焦课程项目后端骨架，依据需求文档和网页原型划分用户、资源、文件、审核、互动、求资源、会员积分、举报投诉、通知和后台管理模块。

## 技术栈

- Java 21
- Spring Boot 3.3
- Spring Web / Validation / Security
- MySQL 8.x
- JWT 鉴权
- Maven Wrapper

## 快速启动

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

默认服务地址：`http://localhost:8080`

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

## 数据库

数据库初始化脚本位于：

- `backend/src/main/resources/schema.sql`
- `backend/src/main/resources/data.sql`

开发环境建议创建 MySQL 数据库：

```sql
CREATE DATABASE resource_sharing_forum DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后根据 `backend/src/main/resources/application.yml` 修改本地账号密码。

## 接口模块

- `/api/auth/**`：注册、登录、退出
- `/api/users/**`：当前用户资料
- `/api/resources/**`：资源发布、列表、详情、提交审核、收藏、点赞、评分
- `/api/files/**`：附件上传、附件列表、下载入口
- `/api/admin/resources/**`：后台资源审核、驳回、下架、恢复
- `/api/requests/**`：求资源发布、回答、采纳、取消
- `/api/members/**`：会员等级、积分流水、权益
- `/api/reports/**`：举报、版权投诉
- `/api/notifications/**`：通知列表、未读数、已读

所有接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "request-trace-id"
}
```

