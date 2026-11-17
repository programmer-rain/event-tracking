/**
 * 埋点 SDK 核心 —— 教学 Demo 版
 *
 * 五个教学点，对应五个设计决策：
 *  1. eventId 幂等键：全链路去重的题眼，后端 SETNX 就靠它
 *  2. 双阈值攒批：数量够 10 条或等待 3 秒就上报，请求数降 90%
 *  3. 上报降级链：常规用 fetch + keepalive，页面卸载用 sendBeacon
 *  4. 弱网兜底：失败落 localStorage，指数退避 + 随机抖动重试，冷启动补发
 *  5. 一致性采样：按 deviceId 哈希分桶，同一用户要么全采要么全不采，
 *     避免 Math.random() 逐事件抽奖导致 UV 口径失真
 */

export interface TrackEvent {
  /** 全链路幂等键 */
  eventId: string;
  appId: string;
  /** page_view / click / exposure / custom */
  eventType: string;
  eventName: string;
  pageId: string;
  itemId?: string;
  userId?: string;
  deviceId: string;
  sessionId: string;
  clientTs: number;
  props?: Record<string, unknown>;
}

export interface TrackInput {
  eventType?: string;
  pageId?: string;
  itemId?: string;
  props?: Record<string, unknown>;
}

export interface TrackerOptions {
  appId: string;
  endpoint: string;
  userId?: string;
  /** 数量阈值：攒够多少条立即上报 */
  batchSize?: number;
  /** 时间阈值：最多攒多久（ms） */
  flushInterval?: number;
  /** 采样率 0~1，大促可一键降级 */
  sampleRate?: number;
  /** 内存队列上限，满了丢最旧 */
  maxQueue?: number;
}

export interface TrackerDebugInfo {
  queueSize: number;
  sentBatches: number;
  sentEvents: number;
  failedBatches: number;
  beaconFlushes: number;
  pendingSaved: number;
  backoffMs: number;
  simulateFail: boolean;
}

const DEVICE_KEY = '__track_device_id__';
const SESSION_KEY = '__track_session__';
const PENDING_KEY = '__track_pending__';
/** 会话超时：30 分钟无操作算新会话 */
const SESSION_TIMEOUT = 30 * 60 * 1000;

const uuid = (): string =>
  globalThis.crypto?.randomUUID?.() ??
  `${Date.now()}-${Math.random().toString(16).slice(2)}`;

/** deviceId：UV 统计的锚点，localStorage 持久化 */
function getDeviceId(): string {
  let v = localStorage.getItem(DEVICE_KEY);
  if (!v) {
    v = uuid();
    localStorage.setItem(DEVICE_KEY, v);
  }
  return v;
}

/** sessionId：滑动窗口式会话，漏斗串联的口径基础 */
function touchSession(): string {
  const now = Date.now();
  let obj: { id: string; ts: number };
  try {
    obj = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null') ?? {
      id: uuid(),
      ts: now,
    };
    if (now - obj.ts > SESSION_TIMEOUT) {
      obj = { id: uuid(), ts: now };
    } else {
      obj.ts = now;
    }
  } catch {
    obj = { id: uuid(), ts: now };
  }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(obj));
  return obj.id;
}

export class Tracker {
  private queue: TrackEvent[] = [];
  private timer?: number;
  private backoff = 1000;
  private deviceId = getDeviceId();
  private simulateFail = false;
  private listeners = new Set<() => void>();
  private debug = {
    sentBatches: 0,
    sentEvents: 0,
    failedBatches: 0,
    beaconFlushes: 0,
  };

  private opts: Required<Omit<TrackerOptions, 'userId'>> & { userId?: string };

  constructor(options: TrackerOptions) {
    this.opts = {
      batchSize: 10,
      flushInterval: 3000,
      sampleRate: 1,
      maxQueue: 500,
      ...options,
    };
    this.restorePending(); // 冷启动补发：上次弱网没发出去的数据
    this.bindLifecycle();
    this.timer = window.setInterval(() => this.flush(), this.opts.flushInterval);
  }

  /** 登录后设置用户 ID：UV 口径从 deviceId 切换到 userId */
  setUserId(userId?: string) {
    this.opts.userId = userId;
  }

  /** 统一埋点入口：只进内存队列，不做同步 IO，不阻塞业务 */
  track(eventName: string, input: TrackInput = {}) {
    if (!this.sampled()) return;

    const event: TrackEvent = {
      eventId: uuid(),
      appId: this.opts.appId,
      eventType: input.eventType ?? 'custom',
      eventName,
      pageId: input.pageId ?? location.pathname,
      itemId: input.itemId,
      userId: this.opts.userId,
      deviceId: this.deviceId,
      sessionId: touchSession(),
      clientTs: Date.now(),
      props: {
        url: location.href,
        referrer: document.referrer,
        ...input.props,
      },
    };

    // 队列上限保护：满了丢最旧
    if (this.queue.length >= this.opts.maxQueue) {
      this.queue.shift();
    }
    this.queue.push(event);
    this.emitDebug();

    // 数量阈值触发
    if (this.queue.length >= this.opts.batchSize) {
      this.flush();
    }
  }

  /**
   * 上报当前队列。
   * @param useBeacon 页面卸载场景用 sendBeacon（浏览器保证发出，但拿不到回调）
   */
  flush(useBeacon = false) {
    if (!this.queue.length) return;
    // 先摘走整批，防止并发重复发送
    const batch = this.queue.splice(0);
    const body = JSON.stringify({ appId: this.opts.appId, events: batch });

    const handle = (ok: boolean) => {
      if (ok) {
        this.backoff = 1000;
        this.debug.sentBatches++;
        this.debug.sentEvents += batch.length;
        if (useBeacon) this.debug.beaconFlushes++;
        this.clearPending(batch.map((e) => e.eventId));
      } else {
        this.debug.failedBatches++;
        // 回队 + 落盘 + 指数退避重试
        this.queue.unshift(...batch);
        if (this.queue.length > this.opts.maxQueue) {
          this.queue.splice(0, this.queue.length - this.opts.maxQueue);
        }
        this.savePending(batch);
        // 教学点：退避加随机抖动，避免整站用户同一时刻重试打穿后端
        const delay = this.backoff + Math.random() * 300;
        this.backoff = Math.min(this.backoff * 2, 30_000);
        setTimeout(() => this.flush(), delay);
      }
      this.emitDebug();
    };

    // 教学演示开关：模拟弱网 / 服务端故障
    if (this.simulateFail) {
      handle(false);
      return;
    }

    if (useBeacon && navigator.sendBeacon) {
      // 教学点：用 Blob 指定 text/plain 走简单请求，
      // application/json 跨域会触发 CORS 预检，beacon 场景容易失败；
      // 后端用 @RequestBody String 接收后自行 parse
      const ok = navigator.sendBeacon(
        this.opts.endpoint,
        new Blob([body], { type: 'text/plain;charset=UTF-8' }),
      );
      handle(ok);
    } else {
      // 常规通道：fetch + keepalive，页面关闭请求也能继续，且有 Promise 能感知失败
      fetch(this.opts.endpoint, {
        method: 'POST',
        body,
        keepalive: true,
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
      })
        .then((r) => handle(r.ok))
        .catch(() => handle(false));
    }
  }

  /** 卸载时清理：先 flush 再停定时器 */
  destroy() {
    if (this.timer) clearInterval(this.timer);
    this.flush(true);
  }

  // ---------- 教学演示辅助 ----------

  setSimulateFail(v: boolean) {
    this.simulateFail = v;
    this.emitDebug();
  }

  subscribeDebug(cb: () => void): () => void {
    this.listeners.add(cb);
    cb();
    return () => this.listeners.delete(cb);
  }

  getDebug(): TrackerDebugInfo {
    return {
      queueSize: this.queue.length,
      sentBatches: this.debug.sentBatches,
      sentEvents: this.debug.sentEvents,
      failedBatches: this.debug.failedBatches,
      beaconFlushes: this.debug.beaconFlushes,
      pendingSaved: this.readPending().length,
      backoffMs: this.backoff,
      simulateFail: this.simulateFail,
    };
  }

  private emitDebug() {
    this.listeners.forEach((cb) => cb());
  }

  // ---------- 生命周期兜底 ----------

  private bindLifecycle() {
    // 教学点：pagehide 比 unload 可靠（移动端兼容）；切后台也要 flush
    window.addEventListener('pagehide', () => this.flush(true));
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') this.flush(true);
    });
  }

  // ---------- 一致性采样 ----------

  private sampled(): boolean {
    if (this.opts.sampleRate >= 1) return true;
    return this.hash(this.deviceId) < this.opts.sampleRate;
  }

  private hash(str: string): number {
    let h = 0;
    for (let i = 0; i < str.length; i++) {
      h = (h * 31 + str.charCodeAt(i)) | 0;
    }
    return Math.abs(h % 10000) / 10000;
  }

  // ---------- 本地持久化（弱网兜底） ----------

  private readPending(): TrackEvent[] {
    try {
      return JSON.parse(localStorage.getItem(PENDING_KEY) || '[]');
    } catch {
      return [];
    }
  }

  private savePending(batch: TrackEvent[]) {
    const map = new Map<string, TrackEvent>();
    [...this.readPending(), ...batch].forEach((e) => map.set(e.eventId, e));
    // 容量上限：只留最新 200 条，防止 localStorage 爆掉
    localStorage.setItem(
      PENDING_KEY,
      JSON.stringify([...map.values()].slice(-200)),
    );
  }

  private clearPending(ids: string[]) {
    const set = new Set(ids);
    localStorage.setItem(
      PENDING_KEY,
      JSON.stringify(this.readPending().filter((e) => !set.has(e.eventId))),
    );
  }

  private restorePending() {
    const pending = this.readPending();
    if (pending.length) {
      this.queue.unshift(...pending);
      localStorage.removeItem(PENDING_KEY);
    }
  }
}
