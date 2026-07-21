#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
林蛮记账 · 代码审查问题修复验证脚本（TDD 的“测试”层）

用法：
    python tests/verify_review_fixes.py

每一项是审查报告中某条问题的“目标状态”断言。
修复前运行 → 全部 FAIL（红灯）；修复后运行 → 全部 PASS（绿灯）。
既作为本次修复的验收，也作为日后回归检查的基线。
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def p(*a):
    return os.path.join(ROOT, *a)


def read(*a):
    with open(p(*a), encoding="utf-8") as f:
        return f.read()


failures = []


def check(name, cond):
    if cond:
        print(f"  PASS  {name}")
    else:
        print(f"  FAIL  {name}")
        failures.append(name)


print("== 必须修复 1：build_android.sh 签名写回 + 去掉硬编码 JAVA_HOME ==")
sh = read("frontend/build_android.sh")
check("无硬编码 JAVA_HOME=/usr/lib/jvm", "JAVA_HOME=/usr/lib/jvm" not in sh)
check("init 后写回 signingConfigs 块", 'create("release")' in sh and "signingConfigs {" in sh)
check("注入 release signingConfig 引用",
      'signingConfig = signingConfigs.getByName("release")' in sh)
check("保留 versionCode 语义化计算块", "MAJ*10000" in sh)

print("== 必须修复 2：application.yml 密码占位符 + CORS 缩进 ==")
yml = read("backend/src/main/resources/application.yml")
check("DB 密码无明文默认值 WXChen5437@", "WXChen5437@" not in yml)
m = re.search(r"^(\s*)allowed-origins:", yml, re.M)
check("CORS allowed-origins 缩进为 4 空格", m is not None and m.group(1) == "    ")

print("== 必须修复 3：.gitignore 忽略 .env.server ==")
gi = read(".gitignore")
check(".gitignore 含 .env.server", ".env.server" in gi.split())
r = subprocess.run(["git", "check-ignore", "frontend/.env.server"],
                   cwd=ROOT, capture_output=True, text=True)
check("git check-ignore frontend/.env.server 生效", r.returncode == 0)

print("== 建议修改：tauri.conf targets=apk / autoIncrement=false ==")
tc = json.loads(read("frontend/src-tauri/tauri.conf.json"))
check("tauri.conf.json targets == 'apk'", tc["bundle"]["targets"] == "apk")
check("tauri.conf.json autoIncrementVersionCode == false",
      tc["bundle"]["android"]["autoIncrementVersionCode"] is False)

print("== 建议修改：client.ts 401 注入式跳转，不再硬重定向 ==")
ct = read("frontend/src/api/client.ts")
check("client.ts 导出 setUnauthorizedHandler", "export function setUnauthorizedHandler" in ct)
check("client.ts 401 优先走注入式 handler（if (unauthorizedHandler)）",
      "if (unauthorizedHandler)" in ct)

print("== 建议修改：AuthContext 注册跳转处理器 ==")
ac = read("frontend/src/auth/AuthContext.tsx")
check("AuthContext 注册 setUnauthorizedHandler 并调用 navigate",
      "setUnauthorizedHandler" in ac and "navigate(" in ac)

print("== 建议修改：build_android.bat ANDROID_HOME 可移植 ==")
bat = read("frontend/build_android.bat")
check("build_android.bat 用 if not defined ANDROID_HOME 回退",
      "if not defined ANDROID_HOME" in bat)

print("== 建议修改：vite.config 拆 vendor chunk ==")
vc = read("frontend/vite.config.ts")
check("vite.config.ts 配置 manualChunks", "manualChunks" in vc)

print("== 建议修改：分发文档更新为 GitHub Releases 方案 ==")
doc = read("docs/android-public-deploy-plan.md")
check("文档说明 GitHub Releases 下载地址",
      "releases/latest/download" in doc or "GitHub Releases" in doc)

print()
if failures:
    print(f"[红灯] {len(failures)} 项未通过：")
    for f in failures:
        print(f"   - {f}")
    sys.exit(1)
print("[绿灯] 全部通过 ✅")
