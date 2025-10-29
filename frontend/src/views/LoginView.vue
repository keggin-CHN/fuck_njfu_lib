<template>
  <div class="auth-wrapper">
    <div class="card" style="max-width:420px;width:100%;">
      <h2 style="margin-top:0;margin-bottom:1rem;">登录账号</h2>
      <p style="color:#6b7280;margin-top:0;margin-bottom:2rem;">欢迎回来，请使用校园账号登录系统。</p>
      <form @submit.prevent="submit">
        <div class="form-group">
          <label>学号</label>
          <input v-model="form.username" class="input" placeholder="请输入学号" required />
        </div>
        <div class="form-group">
          <label>平台密码</label>
          <input v-model="form.password" type="password" class="input" placeholder="请输入密码" required />
        </div>
        <button class="btn" style="width:100%;margin-top:1.5rem;" :disabled="userStore.loading">
          {{ userStore.loading ? '登录中...' : '登录' }}
        </button>
        <p v-if="userStore.error" style="color:#dc2626;margin-top:1rem;">{{ userStore.error }}</p>
      </form>
      <p style="margin-top:1.5rem;color:#4b5563;">
        还没有账号？<RouterLink to="/register">立即注册</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '../stores/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const form = reactive({
  username: '',
  password: ''
});

const submit = async () => {
  const success = await userStore.login(form);
  if (success) {
    const redirect = route.query.redirect || '/dashboard';
    router.push(redirect);
  }
};
</script>

<style scoped>
.auth-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: radial-gradient(circle at top, rgba(59, 130, 246, 0.15), transparent), #f5f7fa;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 1.1rem;
}

label {
  font-weight: 600;
  color: #475569;
}
</style>
