"""
测试后端服务 - 回显请求信息，用于验证网关转发功能
启动方式：python test_backend.py 8081 user-service user-1
"""

import sys
import json
from flask import Flask, request, jsonify

app = Flask(__name__)

# 服务实例元信息（通过启动参数注入）
INSTANCE_META = {}


@app.route("/<path:path>", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
@app.route("/", defaults={"path": ""}, methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
def echo(path):
    """回显请求详情"""
    response = {
        "serviceId": INSTANCE_META.get("serviceId", "unknown"),
        "instanceId": INSTANCE_META.get("instanceId", "unknown"),
        "port": INSTANCE_META.get("port", 0),
        "request": {
            "method": request.method,
            "path": "/" + path,
            "fullPath": request.full_path,
            "headers": dict(request.headers),
            "query": dict(request.args),
            "body": request.get_data(as_text=True),
            "remoteAddr": request.remote_addr,
            "clientIp": request.headers.get("X-Forwarded-For", request.remote_addr),
        },
    }
    return jsonify(response)


@app.route("/health")
def health():
    return "OK", 200


def parse_args():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8081
    service_id = sys.argv[2] if len(sys.argv) > 2 else "test-service"
    instance_id = sys.argv[3] if len(sys.argv) > 3 else f"test-{port}"
    return port, service_id, instance_id


if __name__ == "__main__":
    port, service_id, instance_id = parse_args()
    INSTANCE_META = {"port": port, "serviceId": service_id, "instanceId": instance_id}
    print(f"Starting {service_id} / {instance_id} on port {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
