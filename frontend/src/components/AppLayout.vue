<template>
  <div class="layout" v-if="userStore.isAuthenticated">
    <aside class="sidebar">
      <div class="brand">📚 图书馆助手</div>
      <RouterLink to="/dashboard" :class="linkClass('/dashboard')">仪表盘</RouterLink>
      <RouterLink to="/reservations" :class="linkClass('/reservations')">预约记录</RouterLink>
      <RouterLink to="/settings" :class="linkClass('/settings')">自动预约设置</RouterLink>
      <RouterLink to="/traffic" :class="linkClass('/traffic')">馆内人数</RouterLink>
      <RouterLink v-if="userStore.isAdmin" to="/admin" :class="linkClass('/admin')">管理员中心</RouterLink>
      <button class="btn" style="margin-top:auto" @click="logout">退出登录</button>
    </aside>
    <main class="content">
      <header style="display:flex;justify-content:space-between;align-items:center;">
        <div>
          <h1 style="margin:0;font-size:1.6rem;font-weight:700;">{{ title }}</h1>
          <p style="margin:0;color:#6b7280;">欢迎回来，{{ userStore.profile?.username }}</p>
        </div>
        <div class="card" style="display:flex;flex-direction:column;gap:0.25rem;min-width:200px;">
          <span style="font-size:0.85rem;color:#6b7280;">今日状态</span>
          <strong style="font-size:1.2rem;">{{ greeting }}</strong>
        </div>
      </header>
      <RouterView />
    </main>
  </div>
  <RouterView v-else />
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '../stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const titleMap = {
  '/dashboard': '仪表盘',
  '/reservations': '预约记录',
  '/settings': '自动预约设置',
  '/traffic': '实时馆内人数',
  '/admin': '管理员控制台'
};

const title = computed(() => titleMap[route.path] || '仪表盘');
const greeting = computed(() => {
  const hours = new Date().getHours();
  if (hours < 6) return '注意休息，夜猫子';
  if (hours < 12) return '上午好，保持专注';
  if (hours < 18) return '下午好，继续加油';
  return '晚上好，别忘了休息';
});

const linkClass = (path) => ({
  active: route.path === path
});

const logout = () => {
  userStore.logout();
  router.push({ name: 'login' });
};
</script>
