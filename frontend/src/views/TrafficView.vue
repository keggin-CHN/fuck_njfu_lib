<template>
  <div class="card" style="display:flex;flex-direction:column;gap:1.5rem;">
    <header style="display:flex;justify-content:space-between;align-items:center;">
      <div>
        <h2 style="margin:0;">馆内人数监控</h2>
        <p style="margin:0;color:#6b7280;">系统每 5 分钟自动采集数据，帮助你判断当前人流情况。</p>
      </div>
      <button class="btn" style="padding:0.45rem 1rem;font-size:0.9rem;" @click="downloadCsv">导出 CSV</button>
    </header>

    <div class="stat-card" style="display:flex;flex-direction:column;gap:0.5rem;">
      <span style="color:#6b7280;">最新数据</span>
      <strong style="font-size:2rem;">{{ latest?.count ?? '--' }} 人</strong>
      <span style="color:#9ca3af;">时间：{{ formatDateTime(latest?.timestamp) }}</span>
    </div>

    <table>
      <thead>
        <tr>
          <th>时间</th>
          <th>在馆人数</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in data" :key="item.timestamp">
          <td>{{ formatDateTime(item.timestamp) }}</td>
          <td>{{ item.count }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { trafficApi } from '../api';

const data = ref([]);
const latest = ref(null);

const loadData = async () => {
  const [recentRes, latestRes] = await Promise.all([
    trafficApi.recent(24).catch(() => ({ data: [] })),
    trafficApi.latest().catch(() => ({ data: null }))
  ]);
  data.value = recentRes.data || [];
  latest.value = latestRes.data;
};

const downloadCsv = async () => {
  const response = await trafficApi.exportCsv();
  const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', 'traffic.csv');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
};

const formatDateTime = (timestamp) => {
  if (!timestamp) return '-';
  return new Date(timestamp * 1000).toLocaleString();
};

onMounted(loadData);
</script>
