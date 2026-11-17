import { createRouter, createWebHistory } from 'vue-router';
import type { Tracker } from './track/tracker';
import ProductPage from './views/ProductPage.vue';
import DashboardPage from './views/DashboardPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'products',
      component: ProductPage,
      meta: { pageCode: 'product_list', title: '商品列表' },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardPage,
      meta: { pageCode: 'realtime_dashboard', title: '实时看板' },
    },
  ],
});

/**
 * 路由级自动 PV：统一在 afterEach 上报，页面组件里不要再手动报。
 * 教学点（踩坑提醒）：路由自动报了 page_view，组件里再报一次，PV 直接翻倍。
 */
export function setupRouterTrack(tracker: Tracker) {
  router.afterEach((to) => {
    tracker.track('page_view', {
      eventType: 'page_view',
      pageId: (to.meta.pageCode as string) || to.path,
      props: {
        title: (to.meta.title as string) || document.title,
        fullPath: to.fullPath,
      },
    });
  });
}
