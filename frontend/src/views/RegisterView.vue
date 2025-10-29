<template>
  <div class="auth-wrapper">
    <div class="card" style="max-width:520px;width:100%;">
      <h2 style="margin:0 0 1rem 0;">注册新账号</h2>
      <p style="color:#6b7280;margin:0 0 1.5rem 0;">首次注册的用户将自动成为管理员，其余用户需提供邀请码。</p>
      <form @submit.prevent="submit">
        <div class="grid">
          <div class="form-group">
            <label>学号</label>
            <input v-model="form.username" class="input" placeholder="请输入学号" required />
          </div>
          <div class="form-group">
            <label>平台密码</label>
            <input v-model="form.password" type="password" class="input" placeholder="设置登录密码" required />
          </div>
        </div>
        <div class="grid">
          <div class="form-group">
            <label>统一认证密码</label>
            <input v-model="form.eduPassword" type="password" class="input" placeholder="用于访问校园统一认证" required />
          </div>
          <div class="form-group">
            <label>图书馆密码</label>
            <input v-model="form.libPassword" type="password" class="input" placeholder="用于图书馆预约" required />
          </div>
        </div>
        <div class="form-group">
          <label>邀请码（如需）</label>
          <input v-model="form.inviteCode" class="input" placeholder="如需邀请码，请向管理员索取" />
        </div>
        <button class="btn" style="width:100%;margin-top:1.5rem;" :disabled="userStore.loading">
          {{ userStore.loading ? '注册中...' : '立即注册' }}
        </button>
        <p v-if="userStore.error" style="color:#dc2626;margin-top:1rem;">{{ userStore.error }}</p>
      </form>
      <p style="margin-top:1.5rem;color:#4b5563;">
        已经有账号？<RouterLink to="/login">返回登录</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '../stores/user';

const router = useRouter();
const userStore = useUserStore();

const form = reactive({
  username: '',
  password: '',
  eduPassword: '',
  libPassword: '',
  inviteCode: ''
});

const submit = async () => {
  const success = await userStore.register(form);
  if (success) {
    alert('注册成功，请登录');
    router.push({ name: 'login' });
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
  background: radial-gradient(circle at top, rgba(99, 102, 241, 0.15), transparent), #f5f7fa;
}

.grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
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
