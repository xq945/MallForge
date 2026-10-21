#!/usr/bin/env python3
"""将 mall-swarm/config 下的 dev 配置导入 Nacos（public 命名空间 / DEFAULT_GROUP）。

用法：python scripts/import-nacos-config.py
可用环境变量覆盖：NACOS_ADDR（默认 http://localhost:8848）
"""
import os
import sys
import urllib.parse
import urllib.request

NACOS_ADDR = os.environ.get("NACOS_ADDR", "http://localhost:8848")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

FILES = {
    "mall-admin-dev.yaml": "admin",
    "mall-portal-dev.yaml": "portal",
    "mall-search-dev.yaml": "search",
    "mall-gateway-dev.yaml": "gateway",
}


def resolve_path(data_id: str, sub_dir: str) -> str:
    """优先使用本地覆盖文件（*.local.yaml，已 gitignore），否则用仓库内的示例配置。"""
    path = os.path.join(ROOT, "mall-swarm", "config", sub_dir, data_id)
    local_path = path.replace(".yaml", ".local.yaml")
    return local_path if os.path.isfile(local_path) else path


def main() -> int:
    for data_id, sub_dir in FILES.items():
        path = resolve_path(data_id, sub_dir)
        if not os.path.isfile(path):
            print(f"跳过：找不到 {path}", file=sys.stderr)
            return 1

        with open(path, encoding="utf-8") as f:
            content = f.read()

        body = urllib.parse.urlencode(
            {
                "dataId": data_id,
                "group": "DEFAULT_GROUP",
                "type": "yaml",
                "content": content,
            }
        ).encode("utf-8")
        req = urllib.request.Request(
            f"{NACOS_ADDR}/nacos/v1/cs/configs",
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                result = resp.read().decode("utf-8").strip()
        except Exception as exc:  # noqa: BLE001
            print(f"导入 {data_id} 失败：{exc}", file=sys.stderr)
            return 1

        if result != "true":
            print(f"导入 {data_id} 失败：{result}", file=sys.stderr)
            return 1
        source = "local" if path.endswith(".local.yaml") else "仓库"
        print(f"已导入 {data_id}（来源：{source}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
