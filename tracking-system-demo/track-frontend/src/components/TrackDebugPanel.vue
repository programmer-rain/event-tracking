<script setup lang="ts">
import { inject, onUnmounted, reactive, ref } from 'vue';
import type { Tracker, TrackerDebugInfo } from '../track/tracker';

const tracker = inject<Tracker>('tracker')!;
const open = ref(true);

const info = reactive<TrackerDebugInfo>({
  queueSize: 0,
  sentBatches: 0,
  sentEvents: 0,
  failedBatches: 0,
  beaconFlushes: 0,
  pendingSaved: 0,
  backoffMs: 1000,
  simulateFail: false,
});

const unsubscribe = tracker.subscribeDebug(() => Object.assign(info, tracker.getDebug()));
onUnmounted(unsubscribe);

function flushNow() {
  tracker.flush();
}

function toggleFail() {
  tracker.setSimulateFail(!info.simulateFail);
}
</script>

<template>
  <div v-if="open" class="debug-panel">
    <div class="debug-head" @click="open = false">
      <span>🔬 SDK 调试面板</span>
      <span>—</span>
    </div>
    <div class="debug-body">
      <div class="debug-row">
        <span class="k">内存队列</span>
        <span class="v">{{ info.queueSize }} 条</span>
      </div>
      <div class="debug-row">
        <span class="k">已发送批次 / 事件</span>
        <span class="v good">{{ info.sentBatches }} / {{ info.sentEvents }}</span>
      </div>
      <div class="debug-row">
        <span class="k">失败批次</span>
        <span class="v" :class="info.failedBatches > 0 ? 'bad' : ''">{{ info.failedBatches }}</span>
      </div>
      <div class="debug-row">
        <span class="k">sendBeacon 兜底次数</span>
        <span class="v">{{ info.beaconFlushes }}</span>
      </div>
      <div class="debug-row">
        <span class="k">本地暂存待补发</span>
        <span class="v" :class="info.pendingSaved > 0 ? 'bad' : ''">{{ info.pendingSaved }} 条</span>
      </div>
      <div class="debug-row">
        <span class="k">当前退避间隔</span>
        <span class="v">{{ info.backoffMs }} ms</span>
      </div>
      <div class="debug-actions">
        <button @click="flushNow">立即 flush</button>
        <button :class="{ 'danger-on': info.simulateFail }" @click="toggleFail">
          {{ info.simulateFail ? '恢复网络' : '模拟弱网' }}
        </button>
      </div>
      <p style="color: #9ca3af; margin-top: 8px">
        点「模拟弱网」观察：失败 → 回队 → 落盘 → 指数退避重试的完整兜底链路
      </p>
    </div>
  </div>

  <button v-else class="debug-toggle" title="打开 SDK 调试面板" @click="open = true">🔬</button>
</template>
