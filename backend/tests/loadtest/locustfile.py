"""「林蛮记账」后端 Locust 虚拟用户压测脚本。

覆盖 4 大接口：登录/注册、记账新增、报表/余额、站内信，外加一个混合场景。
每个虚拟用户（HttpUser）模拟一个真实用户：on_start 注册/登录拿 JWT，之后按
wait_time 节奏发起请求。请求失败（HTTP>=500 或业务 code!=0）会被记入 Locust 失败统计。

运行方式（后端需已在 http://localhost:8080 运行）：
    locust -f locustfile.py --host http://localhost:8080          # Web UI 模式
    locust -f locustfile.py AuthUser --host ... --users 50 ...    # 仅跑某场景（命令行末尾传 User 类名）
"""
import itertools
import os
import threading

from locust import HttpUser, between, task

ADMIN_USER = os.getenv("LOADTEST_ADMIN_USER", "admin")
ADMIN_PASS = os.getenv("LOADTEST_ADMIN_PASS", "admin123456")
TEST_PASS = "LoadTest@123"
TODAY = "2026-07-19"  # 与当前月份一致，保证计入月报
THIS_MONTH = "2026-07"

_counter = itertools.count()
_lock = threading.Lock()


def next_username():
    with _lock:
        return f"loadtest_{next(_counter)}"


def ok_json(resp):
    try:
        return resp.json().get("code") == 0
    except Exception:
        return False


class BaseUser(HttpUser):
    """公共基类：提供 JWT 获取与带鉴权头的请求封装。abstract 不会被直接实例化。"""
    abstract = True
    wait_time = between(0.5, 2.0)
    token = None

    def auth_header(self):
        return {"Authorization": f"Bearer {self.token}"}

    def ensure_token(self):
        """注册一个唯一用户并登录，拿到 JWT；失败则回退用 admin。"""
        uname = next_username()
        self.client.post("/api/auth/register",
                         json={"username": uname, "password": TEST_PASS})
        r = self.client.post("/api/auth/login",
                              json={"username": uname, "password": TEST_PASS})
        if ok_json(r):
            self.token = r.json()["data"]["token"]
        else:
            r = self.client.post("/api/auth/login",
                                 json={"username": ADMIN_USER, "password": ADMIN_PASS})
            self.token = r.json()["data"]["token"]


class AuthUser(BaseUser):
    """登录 / 注册并发场景。"""

    @task(5)
    def login(self):
        with self.client.post("/api/auth/login",
                              json={"username": ADMIN_USER, "password": ADMIN_PASS},
                              name="POST /api/auth/login",
                              catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"login 失败 HTTP={resp.status_code}")

    @task(1)
    def register(self):
        uname = next_username()
        with self.client.post("/api/auth/register",
                              json={"username": uname, "password": TEST_PASS},
                              name="POST /api/auth/register",
                              catch_response=True) as resp:
            # 用户名已存在(409)属预期，不计入失败；仅 5xx 视为失败
            if resp.status_code >= 500:
                resp.failure(f"register 5xx HTTP={resp.status_code}")


class RecordUser(BaseUser):
    """记账新增 + 月报查询并发场景。"""

    def on_start(self):
        self.ensure_token()
        self._i = 0

    @task(4)
    def create_record(self):
        if not self.token:
            return
        self._i += 1
        payload = {
            "type": "income" if self._i % 2 == 0 else "expense",
            "amount": 12.34,
            "recordDate": TODAY,
            "categoryL1": "餐饮",
            "categoryL2": "午餐",
            "note": "loadtest",
        }
        with self.client.post("/api/records", json=payload,
                              headers=self.auth_header(),
                              name="POST /api/records",
                              catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"create record 失败 HTTP={resp.status_code}")

    @task(1)
    def monthly_stats(self):
        if not self.token:
            return
        with self.client.get("/api/records/stats/monthly",
                             params={"month": THIS_MONTH},
                             headers=self.auth_header(),
                             name="GET /api/records/stats/monthly",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"monthly stats 失败 HTTP={resp.status_code}")


class ReportUser(BaseUser):
    """报表/趋势/分类占比并发场景。"""

    def on_start(self):
        self.ensure_token()
        self._i = 0

    @task(3)
    def trend(self):
        if not self.token:
            return
        with self.client.get("/api/reports/trend", params={"months": 12},
                             headers=self.auth_header(),
                             name="GET /api/reports/trend",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"trend 失败 HTTP={resp.status_code}")

    @task(2)
    def category(self):
        if not self.token:
            return
        self._i += 1
        t = "income" if self._i % 2 == 0 else "expense"
        with self.client.get("/api/reports/category-proportion",
                             params={"type": t, "month": THIS_MONTH},
                             headers=self.auth_header(),
                             name="GET /api/reports/category-proportion",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"category 失败 HTTP={resp.status_code}")


class NotificationUser(BaseUser):
    """站内信列表/未读计数/全部已读并发场景。"""

    def on_start(self):
        self.ensure_token()

    @task(4)
    def list_notifications(self):
        if not self.token:
            return
        with self.client.get("/api/notifications",
                             headers=self.auth_header(),
                             name="GET /api/notifications",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"list 失败 HTTP={resp.status_code}")

    @task(3)
    def unread_count(self):
        if not self.token:
            return
        with self.client.get("/api/notifications/unread-count",
                             headers=self.auth_header(),
                             name="GET /api/notifications/unread-count",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"unread-count 失败 HTTP={resp.status_code}")

    @task(1)
    def read_all(self):
        if not self.token:
            return
        with self.client.put("/api/notifications/read-all",
                            headers=self.auth_header(),
                            name="PUT /api/notifications/read-all",
                            catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"read-all 失败 HTTP={resp.status_code}")


class MixedUser(BaseUser):
    """混合真实流量：登录 + 记账 + 报表 + 站内信混合。"""

    def on_start(self):
        self.ensure_token()
        self._i = 0

    @task(2)
    def login(self):
        with self.client.post("/api/auth/login",
                              json={"username": ADMIN_USER, "password": ADMIN_PASS},
                              name="POST /api/auth/login",
                              catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"login 失败 HTTP={resp.status_code}")

    @task(3)
    def create_record(self):
        if not self.token:
            return
        self._i += 1
        payload = {
            "type": "expense",
            "amount": 8.88,
            "recordDate": TODAY,
            "categoryL1": "交通",
            "categoryL2": "地铁",
        }
        with self.client.post("/api/records", json=payload,
                              headers=self.auth_header(),
                              name="POST /api/records",
                              catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"create record 失败 HTTP={resp.status_code}")

    @task(2)
    def trend(self):
        if not self.token:
            return
        with self.client.get("/api/reports/trend", params={"months": 12},
                             headers=self.auth_header(),
                             name="GET /api/reports/trend",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"trend 失败 HTTP={resp.status_code}")

    @task(2)
    def list_notifications(self):
        if not self.token:
            return
        with self.client.get("/api/notifications",
                             headers=self.auth_header(),
                             name="GET /api/notifications",
                             catch_response=True) as resp:
            if not ok_json(resp):
                resp.failure(f"list 失败 HTTP={resp.status_code}")
