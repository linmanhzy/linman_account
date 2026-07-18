# 林蛮记账 · 后端服务（backend）

Spring Boot 3.3 + MySQL 8 后端，提供统一 REST API 供网页端和手机 App 调用。

## 技术栈
- Java 21 / Spring Boot 3.3
- Spring Web（接口）、Spring Data JPA（数据库）
- MySQL 8（开发期连接本机 3306 的 `linman_account` 库）
- Lombok（减少样板代码）、SpringDoc（接口文档）、EasyExcel（Excel 导出）

## 目录结构
```
backend/
├── pom.xml                      # Maven 依赖与构建配置
└── src/main/
    ├── java/com/linman/account/
    │   ├── AccountApplication.java   # 启动类
    │   ├── controller/               # 接收 HTTP 请求（目前有 HealthController）
    │   ├── entity/                   # 实体类（对应数据库表）
    │   └── repository/               # JPA 数据访问
    └── resources/
        ├── application.yml           # 数据源 / JWT 配置
        └── schema.sql                # 6 张表建表脚本（启动时自动执行）
```

## 如何运行
1. 确保本机 MySQL 已启动，且存在 `linman_account` 数据库（已帮你建好）。
2. 在项目根目录执行：
   ```bash
   mvn -f backend/pom.xml spring-boot:run
   ```
3. 启动后访问：
   - 健康检查：`http://127.0.0.1:8080/api/health`
   - 接口文档：`http://127.0.0.1:8080/swagger-ui.html`

> 连接密码通过环境变量 `DB_PASSWORD` 注入；未设置时回退到本地开发默认值（见 application.yml）。
> 生产环境请改用环境变量 / Docker Secret，不要提交真实密码。
