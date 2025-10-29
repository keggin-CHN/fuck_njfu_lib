<template>
  <div style="display:flex;flex-direction:column;gap:1.5rem;">
    <section class="card">
      <header style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
        <div>
          <h2 style="margin:0;">邀请码管理</h2>
          <p style="margin:0;color:#6b7280;">为新用户生成或回收邀请码。</p>
        </div>
        <div style="display:flex;gap:0.75rem;align-items:center;">
          <input v-model.number="inviteCount" type="number" min="1" max="20" class="input" style="width:80px;" />
          <button class="btn" @click="createInvites">生成</button>
        </div>
      </header>
      <table>
        <thead>
          <tr>
            <th>邀请码</th>
            <th>创建者</th>
            <th>状态</th>
            <th>使用者</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="code in inviteCodes" :key="code.id">
            <td>{{ code.code }}</td>
            <td>{{ code.createdBy }}</td>
            <td>{{ code.isUsed ? '已使用' : '未使用' }}</td>
            <td>{{ code.usedBy || '-' }}</td>
            <td>
              <button
                class="btn"
                style="padding:0.35rem 0.8rem;font-size:0.85rem;background:#dc2626;"
                v-if="!code.isUsed"
                @click="deleteInvite(code.code)"
              >删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <header style="margin-bottom:1rem;">
        <h2 style="margin:0;">系统设置</h2>
      </header>
      <div class="grid">
        <div v-for="item in systemSettings" :key="item.key" class="setting-item">
          <label>{{ item.key }}</label>
          <input v-model="item.value" class="input" />
          <small style="color:#94a3b8;">{{ item.description }}</small>
        </div>
      </div>
      <button class="btn" style="align-self:flex-start;margin-top:1rem;" @click="saveSettings">保存设置</button>
    </section>

    <section class="card">
      <header style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
        <h2 style="margin:0;">最近日志</h2>
        <button class="btn" style="padding:0.45rem 1rem;font-size:0.9rem;" @click="loadLogs">刷新</button>
      </header>
      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>用户</th>
            <th>IP</th>
            <th>操作</th>
            <th>状态码</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>{{ formatDateTime(log.createdAt) }}</td>
            <td>{{ log.userId || '-' }}</td>
            <td>{{ log.ipAddress }}</td>
            <td>{{ log.action }}</td>
            <td>{{ log.responseCode }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <header style="margin-bottom:1rem;">
        <h2 style="margin:0;">用户列表</h2>
      </header>
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>学号</th>
            <th>管理员</th>
            <th>创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.id }}</td>
            <td>{{ user.username }}</td>
            <td>{{ user.isAdmin ? '是' : '否' }}</td>
            <td>{{ formatDateTime(user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { adminApi } from '../api';

const inviteCodes = ref([]);
const inviteCount = ref(3);
const systemSettings = ref([]);
const logs = ref([]);
const users = ref([]);

const loadInvites = async () => {
  const { data } = await adminApi.inviteCodes().catch(() => ({ data: [] }));
  inviteCodes.value = data;
};

const createInvites = async () => {
  await adminApi.createInviteCodes(inviteCount.value || 1);
  await loadInvites();
};

const deleteInvite = async (code) => {
  if (!confirm('确定删除该邀请码吗？')) return;
  await adminApi.deleteInviteCode(code);
  await loadInvites();
};

const loadSystemSettings = async () => {
  const { data } = await adminApi.systemSettings().catch(() => ({ data: [] }));
  systemSettings.value = data;
};

const saveSettings = async () => {
  await adminApi.updateSystemSettings(systemSettings.value);
  alert('系统设置已保存');
};

const loadLogs = async () => {
  const { data } = await adminApi.logs().catch(() => ({ data: [] }));
  logs.value = data;
};

const loadUsers = async () => {
  const { data } = await adminApi.users().catch(() => ({ data: [] }));
  users.value = data;
};

const formatDateTime = (value) => (value ? new Date(value).toLocaleString() : '-');

onMounted(async () => {
  await Promise.all([loadInvites(), loadSystemSettings(), loadLogs(), loadUsers()]);
});
</script>

<style scoped>
.setting-item {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.setting-item small {
  font-size: 0.8rem;
}
</style>
