#!/usr/bin/env bash
# 下载 IK 中文分词插件到 docker/elasticsearch/plugins/ik
# mall-search 的索引映射使用 ik_max_word，ES 容器必须装该插件
# 用法：bash scripts/setup-es-ik.sh
set -euo pipefail

ES_VERSION="${ES_VERSION:-8.18.8}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="${ROOT}/docker/elasticsearch"

mkdir -p "${DIR}/plugins/ik"
echo "下载 IK ${ES_VERSION} ..."
curl -fSL -o "${DIR}/ik.zip" \
  "https://get.infini.cloud/elasticsearch/analysis-ik/${ES_VERSION}"

python - "${DIR}/ik.zip" "${DIR}/plugins/ik" <<'PY'
import sys
import zipfile

zip_path, target = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(zip_path) as z:
    z.extractall(target)
print(f"已解压到 {target}")
PY

echo "完成。若 ES 已在运行，执行：docker compose up -d elasticsearch"
