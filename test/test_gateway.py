"""
Day 3 网关功能测试脚本
前置条件：
  1. 后端服务已启动（test_backend.py 8081 / 8082 / 9001）
  2. 网关已启动（GatewayServer main）
测试内容：
  - 路由匹配（精确路径、通配符、404）
  - 负载均衡（轮询/加权分发到不同实例）
  - 请求转发（Header、Query、Body 正确传递）
  - 代理头（X-Forwarded-For、X-Real-IP）
  - 错误处理（404、503）
"""

import json
import requests
import sys

GATEWAY = "http://localhost:8080"


def section(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")


def check(label, condition, detail=""):
    status = "PASS" if condition else "FAIL"
    print(f"  [{status}] {label}")
    if detail and not condition:
        print(f"         {detail}")


# ============================================================
#  1. 路由匹配
# ============================================================
def test_route_matching():
    section("1. 路由匹配")

    # 精确匹配 user-service
    r = requests.get(f"{GATEWAY}/api/user/123")
    data = r.json()
    check("GET /api/user/123 → user-service",
          data["serviceId"] == "user-service",
          f"got: {data['serviceId']}")

    # 精确匹配 order-service
    r = requests.get(f"{GATEWAY}/api/order/456")
    data = r.json()
    check("GET /api/order/456 → order-service",
          data["serviceId"] == "order-service",
          f"got: {data['serviceId']}")

    # 多段通配符
    r = requests.get(f"{GATEWAY}/api/user/123/profile/settings")
    data = r.json()
    check("GET /api/user/123/profile/settings → user-service (多段通配)",
          data["serviceId"] == "user-service",
          f"got: {data['serviceId']}")

    # 无匹配路由 → 404
    r = requests.get(f"{GATEWAY}/api/product/789")
    check("GET /api/product/789 → 404",
          r.status_code == 404,
          f"got: {r.status_code}")


# ============================================================
#  2. 负载均衡 - 验证请求分发到不同实例
# ============================================================
def test_load_balancing():
    section("2. 负载均衡")

    instances_hit = set()
    for _ in range(10):
        r = requests.get(f"{GATEWAY}/api/user/test")
        data = r.json()
        instances_hit.add(data["instanceId"])

    check("10次请求 /api/user → 命中多个实例",
          len(instances_hit) > 1,
          f"命中实例: {instances_hit}")

    # order-service 只有1个实例，应该始终命中同一个
    instances_hit = set()
    for _ in range(5):
        r = requests.get(f"{GATEWAY}/api/order/test")
        data = r.json()
        instances_hit.add(data["instanceId"])

    check("5次请求 /api/order → 始终命中 order-1",
          instances_hit == {"order-1"},
          f"命中实例: {instances_hit}")


# ============================================================
#  3. 请求转发 - Header / Query / Body
# ============================================================
def test_request_forwarding():
    section("3. 请求转发完整性")

    # Query 参数
    r = requests.get(f"{GATEWAY}/api/user/search", params={"q": "hello", "page": "1"})
    data = r.json()
    check("Query参数 q=hello",
          data["request"]["query"].get("q") == "hello",
          f"got: {data['request']['query']}")
    check("Query参数 page=1",
          data["request"]["query"].get("page") == "1")

    # 自定义 Header
    headers = {"X-Custom-Test": "gateway-value", "Authorization": "Bearer token123"}
    r = requests.get(f"{GATEWAY}/api/user/profile", headers=headers)
    data = r.json()
    check("自定义Header X-Custom-Test",
          data["request"]["headers"].get("X-Custom-Test") == "gateway-value")
    check("Authorization头",
          data["request"]["headers"].get("Authorization") == "Bearer token123")

    # POST Body
    body = {"name": "test", "value": 42}
    r = requests.post(f"{GATEWAY}/api/user/create", json=body)
    data = r.json()
    check("POST Body传递",
          "test" in data["request"]["body"],
          f"body: {data['request']['body'][:100]}")

    # 同名Header逗号拼接 - 用原始socket发送重复header
    import socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    sock.connect(("127.0.0.1", 8080))
    raw_req = (
        "GET /api/user/test HTTP/1.1\r\n"
        "Host: 127.0.0.1:8080\r\n"
        "Accept: text/html\r\n"
        "Accept: application/json\r\n"
        "\r\n"
    )
    sock.sendall(raw_req.encode())
    resp_data = b""
    while b"\r\n\r\n" not in resp_data:
        resp_data += sock.recv(4096)
    # 读取body（根据Content-Length）
    header_end = resp_data.index(b"\r\n\r\n") + 4
    headers_part = resp_data[:header_end].decode()
    body_part = resp_data[header_end:]
    for line in headers_part.split("\r\n"):
        if line.lower().startswith("content-length:"):
            cl = int(line.split(":")[1].strip())
            while len(body_part) < cl:
                body_part += sock.recv(4096)
            break
    sock.close()
    data = json.loads(body_part)
    accept = data["request"]["headers"].get("Accept", "")
    check("同名Header逗号拼接 Accept",
          "text/html" in accept and "application/json" in accept,
          f"got: {accept}")


# ============================================================
#  4. 代理头 - X-Forwarded-For / X-Real-IP
# ============================================================
def test_proxy_headers():
    section("4. 代理头")

    r = requests.get(f"{GATEWAY}/api/user/test")
    data = r.json()
    xff = data["request"]["headers"].get("X-Forwarded-For", "")
    xreal = data["request"]["headers"].get("X-Real-Ip", "")
    check("X-Forwarded-For 已设置",
          len(xff) > 0,
          f"got: {xff}")
    check("X-Real-Ip 已设置",
          len(xreal) > 0,
          f"got: {xreal}")

    # 客户端自带头时，网关覆盖为真实IP
    headers = {"X-Forwarded-For": "1.2.3.4"}
    r = requests.get(f"{GATEWAY}/api/user/test", headers=headers)
    data = r.json()
    xff = data["request"]["headers"].get("X-Forwarded-For", "")
    check("客户端带XFF时网关覆盖为真实IP（不是1.2.3.4）",
          xff != "1.2.3.4",
          f"got: {xff}")


# ============================================================
#  5. 不同HTTP方法
# ============================================================
def test_http_methods():
    section("5. HTTP方法")

    for method in ["GET", "POST", "PUT", "DELETE", "PATCH"]:
        r = getattr(requests, method.lower())(f"{GATEWAY}/api/user/method-test")
        data = r.json()
        check(f"{method} /api/user/method-test",
              data["request"]["method"] == method,
              f"got: {data['request']['method']}")


# ============================================================
#  6. 异常场景
# ============================================================
def test_error_cases():
    section("6. 异常场景")

    r = requests.get(f"{GATEWAY}/nonexistent/path")
    check("不存在的路由 → 404",
          r.status_code == 404,
          f"got: {r.status_code}")


# ============================================================
#  Main
# ============================================================
if __name__ == "__main__":
    try:
        requests.get(f"{GATEWAY}/health", timeout=3)
    except Exception:
        print(f"无法连接网关 {GATEWAY}，请确认网关和后端服务已启动")
        sys.exit(1)

    test_route_matching()
    test_load_balancing()
    test_request_forwarding()
    test_proxy_headers()
    test_http_methods()
    test_error_cases()

    print(f"\n{'='*60}")
    print("  测试完成")
    print(f"{'='*60}")
