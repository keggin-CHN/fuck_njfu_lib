<template>
  <div class="card" style="display:flex;flex-direction:column;gap:1.5rem;">
    <header>
      <h2 style="margin:0;">自动预约设置</h2>
      <p style="margin:0;color:#6b7280;">配置自动抢座、迟到保护等功能，系统会按照设定自动执行。</p>
    </header>
    <form class="settings-form" @submit.prevent="submit">
      <div class="grid">
        <div>
          <label>默认区域</label>
          <input v-model="form.area" class="input" placeholder="例如：二楼自习区" required />
        </div>
        <div>
          <label>目标座位号</label>
          <input v-model.number="form.seatNumber" type="number" class="input" placeholder="支持自动寻座" />
        </div>
        <div>
          <label>开始时间</label>
          <input v-model="form.startTime" type="time" class="input" required />
        </div>
        <div>
          <label>结束时间</label>
          <input v-model="form.endTime" type="time" class="input" required />
        </div>
      </div>

      <div class="toggle-list">
        <label class="toggle-item">
          <input type="checkbox" v-model="form.autoReserve" />
          <span>启用自动预约</span>
        </label>
        <label class="toggle-item">
          <input type="checkbox" v-model="form.preventLate" />
          <span>开启迟到保护（自动签到）</span>
        </label>
        <label class="toggle-item">
          <input type="checkbox" v-model="form.autoFindSeat" />
          <span>当座位不可用时自动寻座</span>
        </label>
      </div>

      <button class="btn" style="align-self:flex-start;" :disabled="loading">
        {{ loading ? '保存中...' : '保存设置' }}
      </button>
      <p v-if="message" :style="{ color: messageColor }" style="margin:0;">{{ message }}</p>
    </form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { settingApi } from '../api';

const form = reactive({
  area: '',
  seatNumber: 0,
  startTime: '08:00',
  endTime: '22:00',
  autoReserve: false,
  preventLate: false,
  autoFindSeat: false
});

const loading = ref(false);
const message = ref('');
const messageColor = ref('#16a34a');

const loadSettings = async () => {
  const { data } = await settingApi.get().catch(() => ({ data: null }));
  if (data) {
    Object.assign(form, data);
  }
};

const submit = async () => {
  loading.value = true;
  message.value = '';
  try {
    await settingApi.update(form);
    message.value = '保存成功';
    messageColor.value = '#16a34a';
  } catch (error) {
    message.value = error.response?.data?.error || '保存失败';
    messageColor.value = '#dc2626';
  } finally {
    loading.value = false;
  }
};

onMounted(loadSettings);
</script>

<style scoped>
.settings-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.toggle-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-weight: 600;
  color: #475569;
}

.toggle-item input {
  width: 18px;
  height: 18px;
}

label {
  font-weight: 600;
  color: #475569;
  margin-bottom: 0.35rem;
  display: block;
}
</style>
