<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue';

interface Overview {
  date: string;
  pagePv: number;
  pageUv: number;
  clicks: number;
  exposures: number;
}

interface TopItem {
  itemId: string;
  clicks: number;
}

interface IngestStatus {
  mode: string;
  accepted: number;
  rejected: number;
  dropped: number;
  pending: number;
}

const overview = reactive<Overview>({ date: '', pagePv: 0, pageUv: 0, clicks: 0, exposures: 0 });
const topItems = ref<TopItem[]>([]);
const status = reactive<IngestStatus>({ mode: '', accepted: 0, rejected: 0, dropped: 0, pending: 0 });
const error = ref('');
let timer: number | undefined;

async function refresh() {
  try {
    const [o, t, s] = await Promise.all([
      fetch('/api/analytics/overview').then((r) => r.json()),
      fetch('/api/analytics/top-items?limit=10').then((r) => r.json()),
      fetch('/api/analytics/ingest-status').then((r) => r.json()),
    ]);
    Object.assign(overview, o);
    topItems.value = t;
    Object.assign(status, s);
    error.value = '';
  } catch {
    error.value = '连不上后端：请确认 track-backend 已启动（http://localhost:8080）';
  }
}

onMounted(() => {
  refresh();
  timer = window.setInterval(refresh, 2000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <h2 style="margin-bottom: 16px">
    实时看板
    <span v-if="status.mode" class="badge" style="vertical-align: middle">{{ status.mode }}</span>
  </h2>

  <div v-if="error" class="error-banner">{{ error }}</div>

  <div class="stat-grid">
    <div class="stat-card">
      <div class="label">页面 PV（page_view 次数）</div>
      <div class="value">{{ overview.pagePv }}</div>
    </div>
    <div class="stat-card">
      <div class="label">页面 UV（去重访客）</div>
      <div class="value">{{ overview.pageUv }}</div>
    </div>
    <div class="stat-card">
      <div class="label">点击总数</div>
      <div class="value">{{ overview.clicks }}</div>
    </div>
    <div class="stat-card">
      <div class="label">有效曝光总数</div>
      <div class="value">{{ overview.exposures }}</div>
    </div>
  </div>

  <div class="panel">
    <h2>商品点击排行</h2>
    <table>
      <thead>
        <tr>
          <th>排名</th>
          <th>商品 ID</th>
          <th>点击次数</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!topItems.length">
          <td colspan="3" class="hint">暂无数据，去商品页点几下试试</td>
        </tr>
        <tr v-for="(item, i) in topItems" :key="item.itemId">
          <td>{{ i + 1 }}</td>
          <td>{{ item.itemId }}</td>
          <td>{{ item.clicks }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="panel">
    <h2>接入层状态（可观测性教学点）</h2>
    <table>
      <tbody>
        <tr>
          <th>服务端已接受</th>
          <td>{{ status.accepted }}</td>
        </tr>
        <tr>
          <th>服务端已拒绝（校验失败 / 幂等去重）</th>
          <td>{{ status.rejected }}</td>
        </tr>
        <tr>
          <th>背压丢弃（队列满）</th>
          <td>
            <span :class="status.dropped > 0 ? 'badge warn' : 'badge'">{{ status.dropped }}</span>
          </td>
        </tr>
        <tr>
          <th>管道待处理</th>
          <td>{{ status.pending }}</td>
        </tr>
      </tbody>
    </table>
    <p class="hint">
      试试运行 scripts/load_gen.py 压测脚本：观察 rejected（重复事件被幂等去重）
      与 dropped（队列背压丢弃）的变化，理解"宁可丢新，不拖垮系统"。
    </p>
  </div>
</template>
