# 契约夹具索引

> 本目录 = 真身。位于 Android 工程内，随 Git 提交，clone 下来即可跑测试。  
> Android 侧：`ContractTest` 通过 classpath 直接读取，无同步任务、无绝对路径。  
> 后端侧：pytest 读取工作区 `docs/contract/`（本目录的副本）。  
> 改动规则：夹具改动 = 契约变更，必须同步修改《接口契约.md》并升版本号；
> 改完先跑 `./gradlew test`，绿了再复制到 `docs/contract/`。

## 一、契约 §5 要求的 7 个

| 文件 | 对应接口 | 断言重点 |
|---|---|---|
| `latest.json` | `GET /latest` | 7 个字段齐全，`db` 为 `null`，`online=true` |
| `config.json` | `GET /config` | `metrics` / `thresholds` / `interval` / `volume` / `modes` / `commands` 六块 |
| `history.json` | `GET /history` | `bucket=5m`、`downsampled=true`、`points[].ts/value/min/max` |
| `alarms.json` | `GET /alarms` | 2 条，含 `level=warn` 与 `level=danger`，`ack` 一真一假 |
| `chat.json` | `POST /chat/text` | `action` 非空，含 `cmd_id` 与 `display_text` |
| `command_ok.json` | `POST /command` | `ok=true` + `cmd_id` |
| `error_offline.json` | 任意接口 503 | `error.code=DEVICE_OFFLINE`，三字段齐全 |

## 二、健壮性补充 6 个（评审 P2-19）

这些不是契约新增字段，而是**同一契约下的边界数据**，用来覆盖最易崩的分支。

| 文件 | 用途 | 不处理会怎样 |
|---|---|---|
| `latest_offline.json` | `online=false` 且数值全 `null` | 直接 `String.format` 会 NPE，或显示 `null` 而非 `--` |
| `history_empty.json` | `points: []` | 图表库收到空数组时崩溃或画一条直线 |
| `alarms_empty.json` | `items: []` | RecyclerView 空列表无空态提示，页面一片白 |
| `chat_no_action.json` | `action: null` | 前端直接取 `action.displayText` 会 NPE |
| `error_400.json` | `INVALID_PARAM` | 只处理 503 时，400 会走到未知分支 |
| `error_504.json` | `AI_TIMEOUT` | 误把超时当网络错误，提示文案不对 |

## 三、共同约定

- 设备 ID 统一 `s3-01`，时间戳统一在 `1730000000` 附近（2024-10-27），便于断言；
- 全部 UTF-8 无 BOM，缩进 2 空格；
- 数值保留契约规定的精度（`temp`/`humi` 1 位小数，`lux` 整数）；
- 错误体统一 `{"error": {"code", "message", "request_id"}}`。

## 四、后端断言方式

只断言**包含**这些字段且类型一致，**允许**响应出现新增字段：

```python
def test_latest(fixture, client):
    r = client.get("/api/v1/devices/s3-01/latest")
    assert r.status_code == 200
    body = r.json()
    for k, v in fixture.items():
        assert k in body
        if v is not None:
            assert isinstance(body[k], type(v))
```
