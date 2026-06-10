# 资源分享论坛用户端

这是资源分享论坛的用户端 Web 项目。用户端支持两种运行方式：未启动后端时使用 MSW mock 接口完成 UI、页面逻辑和交互演示；启动 Spring Boot 后端后，通过环境变量切换到真实 REST API。

## 技术栈

- React 19
- TypeScript
- Vite
- React Router
- Ant Design
- Axios
- TanStack Query
- Zustand
- MSW，用于本地 mock `/api` / `/api/v1` 接口
- Vitest，用于单元测试
- Playwright，用于端到端测试

## 项目结构

```text
user/
├── e2e/                  # Playwright 端到端测试
├── public/               # 静态资源，包含 MSW worker
├── scripts/              # 本地开发和测试辅助脚本
├── src/
│   ├── api/              # Axios 请求封装与 React Query hooks
│   ├── components/       # 公共 UI 组件
│   ├── data/             # 本地 mock 基础数据
│   ├── mocks/            # MSW mock 接口
│   ├── pages/            # 页面级组件
│   ├── store/            # Zustand 状态管理
│   ├── test/             # Vitest 测试初始化
│   ├── utils/            # 工具函数和表单校验逻辑
│   ├── App.tsx           # 路由配置
│   ├── main.tsx          # 应用入口
│   └── styles.css        # 全局样式
├── index.html            # Vite HTML 入口
├── package.json          # 依赖和脚本
├── vite.config.ts        # Vite 配置
├── vitest.config.ts      # Vitest 配置
├── playwright.config.ts  # Playwright 配置
└── README.md
```

## 已实现功能

- 首页资源概览和搜索入口
- 资源库列表、筛选、排序、分页
- 资源详情、评论、举报、收藏、点赞
- 0-5 分资源评分
- 多附件展示和单附件下载
- 求资源列表、筛选、详情、回答
- 发布资源，支持多个附件
- 发布求资源
- 登录、注册、找回密码
- 个人中心资料维护
- 会员中心：积分成长、积分规则、会员权益、当前权益
- 安全中心：修改密码、绑定邮箱、登录记录
- 消息中心未读数量角标

## 启动方式

如果当前在项目根目录，先进入用户端目录：

```powershell
cd user
```

安装依赖：

```powershell
npm.cmd install
```

启动开发服务器：

```powershell
npm.cmd run dev
```

默认访问地址：

```text
http://127.0.0.1:5173
```

如果 PowerShell 禁止直接运行 `npm`，请使用 `npm.cmd`。

### 连接真实后端

后端默认端口来自仓库 `backend/src/main/resources/application.yml`：

```text
http://localhost:8080
```

用户端已按后端契约适配统一响应壳：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "..."
}
```

在 `user` 目录复制 `.env.example` 并按需调整：

```powershell
Copy-Item .env.example .env.local
```

常用配置：

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_API_PREFIX=/api
VITE_ENABLE_MOCKS=false
```

如果要走后端版本化路由，可改为：

```env
VITE_API_PREFIX=/api/v1
```

如果只做 UI，本地不启动后端：

```env
VITE_API_BASE_URL=
VITE_ENABLE_MOCKS=true
```

## 测试与构建

单元测试：

```powershell
npm.cmd run test
```

端到端测试：

```powershell
npm.cmd run test:e2e
```

生产构建：

```powershell
npm.cmd run build
```

依赖安全审计：

```powershell
npm.cmd audit
```

## 后端接口说明

用户端 API 层位于 `src/api/`。当前已经结合仓库后端接口做了以下适配：

- Axios 自动携带 `Authorization: Bearer <token>`。
- Axios 自动拆包后端 `{ code, message, data, timestamp }` 响应。
- 分页结果从后端 `{ total, list, page, size }` 归一为前端 `{ total, items, page, pageSize }`。
- 资源接口走 `/resources`，支持列表、详情、发布、收藏、点赞、下载、评分、评论。
- 求资源页面仍保留前端路由 `/demands`，但真实接口调用后端 `/requests`。
- `/api` 前缀下个人资料走 `/me`，`/api/v1` 前缀下个人资料走 `/user/profile`。
- 消息中心读取 `/notifications`，未读状态映射为页面使用的 `unread` 字段。

接口请求封装：

```text
src/api/
```

mock 接口：

```text
src/mocks/handlers.ts
```

mock 数据：

```text
src/data/mockRecords.ts
```

如果后端接口继续演进，优先在 `src/api/endpoints.ts` 和 `src/api/adapters.ts` 维护适配，保持页面组件的调用语义稳定。

## 说明

旧的用户端静态 HTML 原型文件已经删除，仅保留 Vite 必需的 `index.html`。本 README 只说明用户端部分。
