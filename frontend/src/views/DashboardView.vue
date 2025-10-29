<template>
  <div style="display:flex;flex-direction:column;gap:1.5rem;">
    <section class="stat-grid">
      <div class="stat-card">
        <span style="color:#6b7280;font-size:0.85rem;">今日预约</span>
        <h3 style="margin:0;font-size:1.8rem;">{{ liveReservations.length }}</h3>
        <p style="margin:0;color:#9ca3af;font-size:0.85rem;">今日及未来预约记录</p>
      </div>
      <div class="stat-card">
        <span style="color:#6b7280;font-size:0.85rem;">最近成功</span>
        <h3 style="margin:0;font-size:1.8rem;">{{ successCount }}</h3>
        <p style="margin:0;color:#9ca3af;font-size:0.85rem;">近 30 条历史记录中的成功次数</p>
      </div>
      <div class="stat-card">
        <span style="color:#6b7280;font-size:0.85rem;">当前馆内人数</span>
        <h3 style="margin:0;font-size:1.8rem;">{{ latestTraffic?.count ?? '--' }}</h3>
        <p style="margin:0;color:#9ca3af;font-size:0.85rem;">来自自动抓取的实时数据</p>
      </div>
    </section>

    <section class="card">
      <header style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
        <div>
          <h2 style="margin:0;">我的预约</h2>
          <p style="margin:0;color:#6b7280;">今日与未来的预约安排</p>
        </div>
      </header>
      <div v-if="liveReservations.length === 0" style="padding:1.5rem;color:#6b7280;">
        暂无预约，立即前往“预约记录”页面创建。
      </div>
      <table v-else>
        <thead>
          <tr>
            <th>日期</th>
            <th>区域</th>
            <th>座位号</th>
            <th>时间段</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in liveReservations" :key="item.uuid">
            <td>{{ formatDate(item.reserveDate) }}</td>
            <td>{{ item.area }}</td>
            <td>{{ item.seatNumber || '-' }}</td>
            <td>{{ item.startTime }} - {{ item.endTime }}</td>
            <td>{{ item.status }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <header style="margin-bottom:1rem;">
        <h2 style="margin:0;">最近预约历史</h2>
      </header>
      <table>
        <thead>
          <tr>
            <th>创建时间</th>
            <th>日期</th>
            <th>区域</th>
            <th>座位</th>
            <th>状态</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in histories" :key="item.id">
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td>{{ formatDate(item.reserveDate) }}</td>
            <td>{{ item.area }}</td>
            <td>{{ item.seatNumber || '-' }}</td>
            <td>{{ item.status }}</td>
            <td>{{ item.message }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue';
import { reservationApi, trafficApi } from '../api';

const liveReservations = ref([]);
const histories = ref([]);
const latestTraffic = ref(null);

const successCount = computed(() => histories.value.filter((item) => item.status === '成功').length);

const loadData = async () => {
  const [liveRes, historyRes, trafficRes] = await Promise.all([
    reservationApi.live().catch(() => ({ data: [] })),
    reservationApi.history().catch(() => ({ data: [] })),
    trafficApi.latest().catch(() => ({ data: null }))
  ]);
  liveReservations.value = liveRes.data || [];
  histories.value = historyRes.data || [];
  latestTraffic.value = trafficRes.data;
};

const formatDate = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleDateString();
};

const formatDateTime = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString();
};

onMounted(loadData);
</script>
