<script setup lang="ts">
import TrackDebugPanel from './components/TrackDebugPanel.vue';
</script>

<template>
  <header class="nav">
    <div class="nav-inner">
      <div class="brand">📊 埋点系统教学 Demo - 更多项目请关注公众号:Rain的Java大神之路</div>
      <nav class="links">
        <RouterLink to="/">商品页</RouterLink>
        <RouterLink to="/dashboard">实时看板</RouterLink>
      </nav>
    </div>
  </header>

  <main class="container">
    <RouterView />
  </main>

  <footer class="footer">
    Vue3 埋点 SDK · Spring Boot 接收 · 双阈值攒批 · sendBeacon 兜底 · 幂等去重 · 实时 PV/UV
  </footer>

  <TrackDebugPanel />
</template>
