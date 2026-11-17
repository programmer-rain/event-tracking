#!/usr/bin/env python3
"""
简易埋点压测脚本 —— 教学演示用（仅依赖 Python 标准库）

能演示什么：
  1. 接收接口的吞吐与限流（调大并发可触发 429）
  2. eventId 幂等去重（脚本会故意发送重复事件，观察 rejected 增长）
  3. 配合实时看板的"接入层状态"面板观察 accepted / rejected / dropped

用法：
  python scripts/load_gen.py [总事件数] [并发线程数]
  python scripts/load_gen.py 5000 8
"""
import json
import random
import sys
import threading
import time
import urllib.request
import uuid

URL = "http://localhost:8080/api/track/collect"
APP_ID = "mall-demo"
PAGES = ["product_list", "product_detail"]
ITEMS = ["sku_1001", "sku_1002", "sku_1003", "sku_1004"]
EVENT_NAMES = ["product_exposure", "product_buy_click", "product_add_cart_click"]

stats = {"ok": 0, "accepted": 0, "rejected": 0, "fail": 0}
lock = threading.Lock()


def make_event():
    return {
        "eventId": str(uuid.uuid4()),
        "appId": APP_ID,
        "eventType": random.choice(["page_view", "click", "exposure"]),
        "eventName": random.choice(EVENT_NAMES),
        "pageId": random.choice(PAGES),
        "itemId": random.choice(ITEMS),
        "deviceId": f"load-device-{random.randint(1, 200)}",
        "sessionId": str(uuid.uuid4()),
        "clientTs": int(time.time() * 1000),
        "props": {"source": "load_gen"},
    }


def worker(n):
    for i in range(n):
        batch = [make_event() for _ in range(10)]
        # 每 10 批塞一批"完全重复"的事件，验证服务端幂等去重
        if i % 10 == 5:
            batch = batch + batch
        payload = json.dumps({"appId": APP_ID, "events": batch}).encode("utf-8")
        req = urllib.request.Request(
            URL, data=payload, headers={"Content-Type": "text/plain"}
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                body = json.loads(resp.read())
                with lock:
                    stats["ok"] += 1
                    stats["accepted"] += body.get("accepted", 0)
                    stats["rejected"] += body.get("rejected", 0)
        except Exception:
            with lock:
                stats["fail"] += 1


def main():
    total = int(sys.argv[1]) if len(sys.argv) > 1 else 2000
    concurrency = int(sys.argv[2]) if len(sys.argv) > 2 else 4
    per = max(1, total // concurrency // 10)  # 每批 10 条

    print(f"开始压测：约 {per * 10 * concurrency} 条事件，{concurrency} 并发")
    start = time.time()
    threads = [threading.Thread(target=worker, args=(per,)) for _ in range(concurrency)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    cost = time.time() - start

    print(f"完成，耗时 {cost:.1f}s")
    print(f"  请求成功: {stats['ok']}，失败: {stats['fail']}")
    print(f"  服务端接受: {stats['accepted']} 条")
    print(f"  服务端拒绝: {stats['rejected']} 条（含幂等去重过滤的重复事件）")
    print(f"  约 {per * 10 * concurrency / max(cost, 0.001):.0f} events/s")
    print("打开实时看板 /dashboard 的『接入层状态』查看计数变化")


if __name__ == "__main__":
    main()
