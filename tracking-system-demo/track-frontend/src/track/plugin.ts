/**
 * Vue3 埋点插件：自定义指令 + 全局事件委托 + 曝光观察器
 *
 * 教学点 1：点击采集用"全局 1 个捕获阶段事件委托"，
 *   - 动态增删元素、虚拟滚动零成本
 *   - 捕获阶段监听，业务代码 @click.stop 也吞不掉埋点
 *
 * 教学点 2：曝光用 IntersectionObserver，
 *   且必须满足"露出 >=50% 面积 + 停留 >=800ms"才算有效曝光，
 *   否则快速划屏全被算成曝光，CTR 口径失真。
 */
import type { App, Directive } from 'vue';
import type { Tracker } from './tracker';

export interface TrackValue {
  /** 事件名，如 product_buy_click */
  event: string;
  /** 商品 / 对象 ID */
  itemId?: string;
  /** true 表示这是一个曝光埋点（走 IntersectionObserver） */
  expose?: boolean;
}

const EXPOSE_AREA = 0.5;   // 露出面积阈值
const EXPOSE_MS = 800;     // 停留时长阈值

export function createTrackPlugin(tracker: Tracker) {
  /** 元素 -> 曝光定时器 */
  const exposeTimers = new WeakMap<HTMLElement, number>();

  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        const el = entry.target as HTMLElement;
        if (entry.isIntersecting && entry.intersectionRatio >= EXPOSE_AREA) {
          if (exposeTimers.has(el)) return;
          // 开始计时，停留满才算有效曝光
          const timer = window.setTimeout(() => {
            tracker.track(el.dataset.trackExpose ?? 'product_exposure', {
              eventType: 'exposure',
              itemId: el.dataset.trackItem,
              props: { ratio: Math.round(entry.intersectionRatio * 100) / 100 },
            });
            io.unobserve(el); // 一个元素只报一次，防重复曝光
            exposeTimers.delete(el);
          }, EXPOSE_MS);
          exposeTimers.set(el, timer);
        } else {
          // 没停留够就划走了，取消计时
          const timer = exposeTimers.get(el);
          if (timer) {
            clearTimeout(timer);
            exposeTimers.delete(el);
          }
        }
      });
    },
    { threshold: [0, EXPOSE_AREA, 1] },
  );

  const vTrack: Directive<HTMLElement, TrackValue> = {
    mounted(el, { value }) {
      if (value.expose) {
        el.dataset.trackExpose = value.event;
        if (value.itemId) el.dataset.trackItem = value.itemId;
        io.observe(el);
      } else {
        el.dataset.trackClick = value.event;
        if (value.itemId) el.dataset.trackItem = value.itemId;
      }
    },
    unmounted(el) {
      io.unobserve(el);
      const timer = exposeTimers.get(el);
      if (timer) clearTimeout(timer);
      exposeTimers.delete(el);
    },
  };

  return {
    install(app: App) {
      // 全局唯一点击委托：capture 阶段，业务 @click.stop 拦不住
      document.addEventListener(
        'click',
        (e) => {
          const el = (e.target as HTMLElement | null)?.closest?.('[data-track-click]');
          if (!el) return;
          tracker.track(el.dataset.trackClick!, {
            eventType: 'click',
            itemId: el.dataset.trackItem,
            props: {
              // 坐标留着，后面可以做热力图分析
              x: e.clientX,
              y: e.clientY,
              text: (el.innerText || '').slice(0, 50),
            },
          });
        },
        { capture: true, passive: true },
      );

      app.directive('track', vTrack);
      app.config.globalProperties.$track = tracker;
      app.provide('tracker', tracker);
    },
  };
}
