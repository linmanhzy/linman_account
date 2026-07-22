# Debug 构建排查计划：解决 "Failed to construct 'URL': invalid URL" 错误

> 创建日期：2026-07-22
> 关联问题：`build_debug_server.bat` 产出的 Debug APK 在登录时报 "Failed to construct 'URL': invalid URL"

---

## 一、现象

`build_debug_server.bat` 打完 APK 安装到手机后，打开 App 能显示登录页，但点击登录时报错：

```
Failed to construct 'URL': invalid URL
```

这是一个来自 WebView 底层（`new URL()` 构造失败）的错误，用户无法从报错信息中定位真实原因。

---

## 二、根因分析

### 2.1 报错链路

```
client.ts 初始化
  → import.meta.env.VITE_API_BASE 为空/未定义
  → API_BASE = ""（回落空字符串）
  → axios.create({ baseURL: "" })
  → 发起请求时 axios 内部 new URL("/api/auth/login", "")
  → 抛出 "Failed to construct 'URL': invalid URL"
```

### 2.2 为什么 VITE_API_BASE 为空

`build_debug_server.bat` 旧版 (修复前) 的注入方式：

```batch
(echo VITE_API_BASE=%SERVER_URL%)> .env
npx tauri android build --debug
```

**问题**：仅依赖 `.env` 文件写入（Vite 优先级中等），而 `tauri android build --debug` 运行 `vite build`（production 模式），`.env` 文件的读取可能因以下原因失败：

| 失败原因 | 说明 |
|----------|------|
| Vite 缓存 | `node_modules/.vite` 缓存了旧值（即使脚本清理了 `dist/` 和 `node_modules/.vite`，Tauri 内部可能还有额外缓存） |
| 优先级被覆盖 | 若存在 `.env.production`、`.env.local` 等文件，可能覆盖 `.env` 的值 |
| 时序问题 | Vite 读取 `.env` 的时机可能在文件写入与构建之间出现竞态 |

### 2.3 对比：CD Release 为什么没问题

CD 的 `build_android.sh` 使用**环境变量注入**（Vite 最高优先级）：

```bash
export VITE_API_BASE  # 进程环境变量，优先级高于一切 .env 文件
```

且有多层防护：
- 变量为空 → `exit 1`（构建直接失败，不会产出有问题的 APK）
- `.env.server` 作为第二行回落

**结论**：CD Release 版本**不受此问题影响**，无需修改。

---

## 三、修复方案

### 3.1 构建脚本修复：`build_debug_server.bat`

**核心改动**：对标 CD 的 `build_android.sh`，改用**进程环境变量注入**：

```batch
REM 修复前（旧版）
(echo VITE_API_BASE=%SERVER_URL%)> .env

REM 修复后（新版）
set VITE_API_BASE=%SERVER_URL%   ← 进程环境变量，Vite 最高优先级
(echo VITE_API_BASE=%SERVER_URL%)> .env  ← .env 同步写入，双保险
```

**额外增加**：
- URL 格式校验（必须以 `http://` 或 `https://` 开头）
- 构建前打印 VITE_API_BASE 值（人工确认）
- 支持 `DEBUG_SERVER_URL` 环境变量覆盖默认地址
- 详细的构建完成提示（APK 位置 + 验证方法）

### 3.2 前端运行时修复：`client.ts` / `apiBase.ts`

**问题**：即使 API_BASE 为空，之前的代码只打 `console.warn`（手机用户看不到）。

**修复**：

1. **提取可测试函数** `resolveApiBase()` → `src/api/apiBase.ts`
   - PROD 模式 + VITE_API_BASE 为空 → **抛出详细中文错误**（而非静默回落空字符串）
   - 错误信息包含 `VITE_API_BASE` 配置指引、默认服务器地址

2. **DOM 可见红色覆盖层** `showFatalErrorOverlay()` → `src/api/apiBase.ts`
   - 将错误注入页面顶部红色背景区域
   - z-index: 99999，确保用户可见
   - 幂等（多次调用不重复注入）

3. **`client.ts`** 改用 try-catch 包裹：
   ```typescript
   try {
     API_BASE = resolveApiBase(import.meta.env.VITE_API_BASE, import.meta.env.DEV)
   } catch (err) {
     showFatalErrorOverlay(err.message)  // 用户可见
     API_BASE = ''  // 回退，axios 也会报错但至少 console + DOM 已有线索
   }
   ```

### 3.3 测试：`src/api/apiBase.test.ts`

14 个单元测试覆盖所有场景：

| 类别 | 用例数 | 覆盖场景 |
|------|--------|----------|
| VITE_API_BASE 已设置 | 3 | 服务器地址、局域网 IP、DEV=true 仍用配置值 |
| DEV 模式未设置 | 2 | undefined、空字符串 → localhost |
| PROD 模式未设置 | 5 | undefined/空字符串抛错、错误信息含中文/关键字/服务器地址 |
| 边界情况 | 3 | 尾部斜杠、仅空格、前后空白 trim |
| DOM 覆盖层 | 1 | 注入红色错误、幂等 |

运行：`cd frontend && npx vitest run src/api/apiBase.test.ts`

---

## 四、变更文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `frontend/build_debug_server.bat` | 重写 | 环境变量注入 + URL 校验 |
| `frontend/src/api/client.ts` | 修改 | 从 apiBase.ts 导入、try-catch 包裹 |
| `frontend/src/api/apiBase.ts` | **新建** | resolveApiBase + showFatalErrorOverlay 纯函数 |
| `frontend/src/api/apiBase.test.ts` | **新建** | 14 个单元测试 |
| `frontend/tsconfig.json` | 修改 | 添加 `types: ["vitest/globals"]` |
| `frontend/vitest.config.ts` | **新建** | vitest 配置 |
| `frontend/package.json` | 修改 | devDependencies 加 vitest、script 加 test |
| `docs/debug-build-troubleshooting.md` | **新建** | 本排查文档 |

---

## 五、验证步骤

### 5.1 单元测试验证（代码层）

```bash
cd frontend
npx vitest run src/api/apiBase.test.ts
# 预期：14 tests passed, 0 failed
```

### 5.2 构建验证（产出 APK）

```bash
cd frontend
build_debug_server.bat
```

确认构建日志中包含：
```
[0/5] URL 格式校验通过：http://47.104.152.25:8080
[1/5] 已设置 VITE_API_BASE=http://47.104.152.25:8080 (进程环境变量，Vite 最高优先级)
BUILD COMPLETE
```

### 5.3 真机验证（安装测试）

1. 安装 `src-tauri\gen\android\app\build\outputs\apk\arm64\debug\app-arm64-debug.apk`
2. 打开 App，应看到登录页正常显示
3. 输入 admin / WXChen5437@ 登录
4. 预期：正常登录进入首页，无任何 URL 相关错误
5. 若仍有问题：参考下方"检查清单"

### 5.4 异常场景验证（模拟 API_BASE 空值）

可以临时修改 `build_debug_server.bat` 的 `DEBUG_SERVER_URL` 为空，重新构建后安装：

1. App 打开后页面顶部应显示**红色错误覆盖层**，内容包含：
   - "[林蛮记账] 后端地址未配置"
   - "构建时未注入 VITE_API_BASE 环境变量"
   - "VITE_API_BASE=http://你的IP:8080"

---

## 六、排查清单（后续如遇到类似问题）

- [ ] 确认 `build_debug_server.bat` 输出了 `[0/5] URL 格式校验通过`
- [ ] 确认构建日志中 VITE_API_BASE 值正确
- [ ] 手机连接电脑后，`chrome://inspect` 查看 WebView Console，看 `API_BASE` 值
- [ ] 检查 `frontend/.env` 当前值是否正确（构建后会还原）
- [ ] 确认服务器后端可公网访问：`curl http://47.104.152.25:8080/api/health`
- [ ] 确认 CORS 配置允许 `https://tauri.localhost`
- [ ] 确认手机网络可达服务器（同一 WiFi、或手机流量可公网访问）
- [ ] 是否有 `.env.production` 文件覆盖了 `.env` 的 VITE_API_BASE

---

## 七、CD Release 版本检查结论

| 检查项 | 结果 |
|--------|------|
| `deploy.yml` 注入 VITE_API_BASE 为环境变量 | ✅ 已注入 (`env.VITE_API_BASE`) |
| `build_android.sh` 变量为空时停止构建 | ✅ `exit 1` |
| `build_android.sh` 多层回落 (.env.server) | ✅ 有回落 |
| Vite 编译时 API_BASE 为空的风险 | ❌ 不存在（环境变量注入 + 空值 panic） |
| `client.ts` (旧版) console.warn 不可见 | ⚠️ 已通过本次修复解决（DOM 覆盖层） |

**结论**：CD Release 版本**无此问题**，不需要修改。本次修复主要针对本地 Debug 构建脚本和行为增强。
