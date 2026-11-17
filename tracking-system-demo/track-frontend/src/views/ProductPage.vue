<script setup lang="ts">
import { inject } from 'vue';
import type { Tracker } from '../track/tracker';
import headphoneImg1 from '../components/img/星声X3无线降噪耳机商品图生成.png';
import headphoneImg2 from '../components/img/极客 GK87 机械键盘.png';
import headphoneImg3 from '../components/img/脉搏 S2 智能手表.png';
import headphoneImg4 from '../components/img/轻旅 20L 双肩包.png';

const tracker = inject<Tracker>('tracker')!;

// 演示商品数据
const products = [
  {
    id: 'sku_1001',
    name: '星声 X3 无线降噪耳机',
    price: 799,
    emoji: '🎧',
    img: headphoneImg1,
  },
  {
    id: 'sku_1002',
    name: '极客 GK87 机械键盘',
    price: 459,
    emoji: '⌨️',
    img: headphoneImg2,
  },
  {
    id: 'sku_1003',
    name: '脉搏 S2 智能手表',
    price: 1299,
    emoji: '⌚',
    img: headphoneImg3,
  },
  {
    id: 'sku_1004',
    name: '轻旅 20L 双肩包',
    price: 259,
    emoji: '🎒',
    img: headphoneImg4,
  },
];

// 编程式埋点示例：复杂交互直接调用 tracker.track()
function claimCoupon() {
  tracker.track('coupon_claim_click', {
    eventType: 'click',
    pageId: 'product_list',
    props: { couponId: 'new_user_20' },
  });
  alert('🎉 新人券已放入卡包（这条是编程式埋点，看调试面板的队列变化）');
}

function hideBrokenImg(e: Event) {
  (e.target as HTMLImageElement).style.display = 'none';
}
</script>

<template>
  <section class="hero">
    <h1>大促会场 · 数码专场</h1>
    <p>
      教学提示：卡片滑入视野停留 0.8 秒触发曝光埋点；点击按钮触发点击埋点；
      右下角调试面板可以实时观察 SDK 队列、攒批与上报过程。
    </p>
  </section>

  <div class="grid">
    <!-- 声明式曝光埋点：v-track 一行搞定，业务代码零侵入 -->
    <div
      v-for="p in products"
      :key="p.id"
      class="card"
      v-track="{ event: 'product_exposure', itemId: p.id, expose: true }"
    >
      <div class="thumb">
        <span>{{ p.emoji }}</span>
        <img :src="p.img" :alt="p.name" loading="lazy" @error="hideBrokenImg" />
      </div>
      <div class="card-body">
        <h3>{{ p.name }}</h3>
        <p class="price">¥{{ p.price }}</p>
        <div class="actions">
          <!-- 声明式点击埋点：全局捕获阶段事件委托采集 -->
          <button class="btn primary" v-track="{ event: 'product_buy_click', itemId: p.id }">
            立即购买
          </button>
          <button class="btn" v-track="{ event: 'product_add_cart_click', itemId: p.id }">
            加入购物车
          </button>
        </div>
      </div>
    </div>
  </div>

  <div class="coupon-tip">
    <div>
      <strong>🎁 新人专享 20 元券</strong>
      <span class="hint">（这个按钮演示编程式埋点：tracker.track() 直接调用）</span>
    </div>
    <button class="btn primary" style="flex: none; padding: 8px 24px" @click="claimCoupon">
      立即领取
    </button>
  </div>
</template>
