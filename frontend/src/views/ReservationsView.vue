<template>
  <div class="card" style="display:flex;flex-direction:column;gap:1.5rem;">
    <section>
      <h2 style="margin:0 0 1rem 0;">手动预约</h2>
      <form class="reservation-form" @submit.prevent="submit">
        <div class="form-row">
          <div>
            <label>预约日期</label>
            <input v-model="form.reserveDate" type="date" class="input" required />
          </div>
          <div>
            <label>区域</label>
            <input v-model="form.area" class="input" placeholder="如：一楼自习区" required />
          </div>
          <div>
            <label>座位号</label>
            <input v-model.number="form.seatNumber" type="number" class="input" placeholder="可留空" />
          </div>
        </div>
        <div class="form-row">
          <div>
            <label>开始时间</label>
            <input v-model="form.startTime" type="time" class="input" required />
          </div>
          <div>
            <label>结束时间</label>
            <input v-model="form.endTime" type="time" class="input" required />
          </div>
        </div>
        <button class="btn" style="align-self:flex-start;margin-top:0.5rem;" :disabled="loading">
          {{ loading ? '提交中...' : '提交预约' }}
        </button>
        <p v-if="error" style="color:#dc2626;">{{ error }}</p>
      </form>
    </section>

    <section>
      <header style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
        <h2 style="margin:0;">预约历史</h2>
        <button class="btn" style="padding:0.4rem 0.9rem;font-size:0.9rem;" @click="loadHistory">刷新</button>
      </header>
      <table>
        <thead>
          <tr>
            <th>创建时间</th>
            <th>日期</th>
            <th>区域</th>
            <th>座位</th>
            <th>时间段</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in histories" :key="item.id">
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td>{{ formatDate(item.reserveDate) }}</td>
            <td>{{ item.area }}</td>
            <td>{{ item.seatNumber || '-' }}</td>
            <td>{{ item.startTime }} - {{ item.endTime }}</td>
            <td>{{ item.status }}</td>
            <td>
              <button
                class="btn"
                style="padding:0.35rem 0.8rem;font-size:0.85rem;background:#dc2626;"
                v-if="item.status === '成功'"
                @click="cancel(item.uuid)"
              >取消</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { reservationApi } from '../api';

const today = new Date().toISOString().split('T')[0];

const form = reactive({
  reserveDate: today,
  area: '',
  seatNumber: null,
  startTime: '08:00',
  endTime: '22:00'
});

const loading = ref(false);
const error = ref('');
const histories = ref([]);

const loadHistory = async () => {
  const { data } = await reservationApi.history().catch(() => ({ data: [] }));
  histories.value = data;
};

const submit = async () => {
  loading.value = true;
  error.value = '';
  try {
    await reservationApi.manual({
      ...form,
      seatNumber: form.seatNumber || 0
    });
    await loadHistory();
    alert('预约成功（模拟）');
  } catch (err) {
    error.value = err.response?.data?.error || '预约失败';
  } finally {
    loading.value = false;
  }
};

const cancel = async (uuid) => {
  if (!uuid) return;
  if (!confirm('确定要取消该预约吗？')) return;
  await reservationApi.cancel({ uuid });
  await loadHistory();
};

const formatDate = (value) => (value ? new Date(value).toLocaleDateString() : '-');
const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

onMounted(loadHistory);
</script>

<style scoped>
.reservation-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-row {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

label {
  font-weight: 600;
  color: #475569;
  display: block;
  margin-bottom: 0.35rem;
}
</style>
