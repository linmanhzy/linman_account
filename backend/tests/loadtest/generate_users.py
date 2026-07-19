"""为「林蛮记账」并发压测预注册一批测试用户（用户名前缀 loadtest_）。

用法：
    python generate_users.py --count 50 --base-url http://localhost:8080
"""
import argparse
import sys

import requests


def main():
    p = argparse.ArgumentParser(description="为林蛮记账并发压测预注册测试用户")
    p.add_argument("--base-url", default="http://localhost:8080")
    p.add_argument("--count", type=int, default=50)
    p.add_argument("--prefix", default="loadtest_")
    p.add_argument("--password", default="LoadTest@123")
    args = p.parse_args()

    # 1) 探活
    try:
        h = requests.get(f"{args.base_url}/api/health", timeout=5)
        if h.status_code != 200:
            print(f"[X] 后端探活失败：HTTP {h.status_code}")
            sys.exit(1)
        print(f"[√] 后端探活成功：{args.base_url}")
    except Exception as e:
        print(f"[X] 无法连接后端 {args.base_url}：{e}")
        sys.exit(1)

    # 2) 批量注册
    ok = 0
    for i in range(args.count):
        u = f"{args.prefix}{i}"
        try:
            r = requests.post(
                f"{args.base_url}/api/auth/register",
                json={"username": u, "password": args.password},
                timeout=5,
            )
            # 200 成功；409 表示已存在（视为已就绪）
            if r.status_code in (200, 201, 409):
                ok += 1
            else:
                print(f"    [!] {u} 注册返回 {r.status_code}：{r.text[:80]}")
        except Exception as e:
            print(f"    [!] {u} 注册异常：{e}")

    print(f"[√] 预注册完成：成功 {ok}/{args.count}（用户名前缀 {args.prefix}）")


if __name__ == "__main__":
    main()
