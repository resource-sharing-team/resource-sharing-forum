# 管理端前端说明

本目录是资源分享论坛项目的管理端前端工程，对应分支 `Web_Admin`。

当前阶段重点是先完成和本地原型 `E:/软工基础/原型/admin` 一致的后台管理界面，方便课堂验收和后续前后端联调。

## 技术栈

- React
- TypeScript
- Vite
- React Router
- Axios
- Vitest
- Testing Library

## 页面范围

当前已实现：

- `/login`：后台登录页
- `/`：内容综合管理
  - 资源审核列表
  - 资源上下架管理
  - 求资源帖子管理
  - 评论内容管理
- `/users`：用户账号管理
- `/reports`：举报版权投诉
  - 举报处理
  - 版权投诉处理
- `/categories`：分类标签管理
- `/config`：系统参数配置
- `/logs`：操作审计日志

## 运行方式

首次进入目录后安装依赖：

```powershell
cd "web_admin"
npm install
```

启动开发服务：

```powershell
npm run dev
```

默认访问地址通常为：

```text
http://127.0.0.1:5173/
```

如果端口被占用，Vite 会自动换到下一个端口，按终端输出访问即可。

## 验证命令

运行测试：

```powershell
npm run test
```

运行生产构建：

```powershell
npm run build
```

## 当前数据策略

当前页面使用 `src/data/mockAdmin.ts` 中的 mock 数据，主要用于还原原型和课堂演示。

API 封装位于：

```text
src/api/client.ts
```

后续联调时，管理端会逐步接入后端 `/api/admin/**` 接口，并继续兼容后端统一响应结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-06-04T23:47:12.000+08:00"
}
```

## 后续任务

- 继续提高页面和原型的视觉一致性
- 补齐更多按钮的 mock 状态变化
- 按后端真实接口逐步替换 mock 数据
- 增加浏览器端页面检查和必要的端到端测试
