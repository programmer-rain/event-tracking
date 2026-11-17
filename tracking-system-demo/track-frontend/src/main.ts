import { createApp } from 'vue';
import App from './App.vue';
import { router, setupRouterTrack } from './router';
import { Tracker } from './track/tracker';
import { createTrackPlugin } from './track/plugin';
import './style.css';

// SDK 全局单例
export const tracker = new Tracker({
  appId: 'mall-demo',
  endpoint: '/api/track/collect', // 建议生产环境用独立上报域名
  batchSize: 10,       // 攒够 10 条立即上报
  flushInterval: 3000, // 最多攒 3 秒
  sampleRate: 1,       // 大促可由配置中心动态下调
  maxQueue: 500,
});

// 模拟登录态：真实项目登录后调用 tracker.setUserId(uid)
tracker.setUserId(localStorage.getItem('demo_uid') || undefined);

const app = createApp(App);
app.use(createTrackPlugin(tracker));
app.use(router);
setupRouterTrack(tracker);
app.mount('#app');
