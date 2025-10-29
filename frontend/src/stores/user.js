import { defineStore } from 'pinia';
import { authApi } from '../api';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('auth_token') || '',
    profile: null,
    loading: false,
    error: null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    isAdmin: (state) => state.profile?.isAdmin || false
  },
  actions: {
    async login(payload) {
      this.loading = true;
      this.error = null;
      try {
        const { data } = await authApi.login(payload);
        this.token = data.token;
        this.profile = data.user;
        localStorage.setItem('auth_token', data.token);
        return true;
      } catch (error) {
        this.error = error.response?.data?.error || '登录失败';
        return false;
      } finally {
        this.loading = false;
      }
    },
    async register(payload) {
      this.loading = true;
      this.error = null;
      try {
        await authApi.register(payload);
        return true;
      } catch (error) {
        this.error = error.response?.data?.error || '注册失败';
        return false;
      } finally {
        this.loading = false;
      }
    },
    async fetchProfile() {
      if (!this.token) return;
      try {
        const { data } = await authApi.profile();
        this.profile = data;
      } catch (error) {
        this.logout();
      }
    },
    logout() {
      this.token = '';
      this.profile = null;
      localStorage.removeItem('auth_token');
    }
  }
});
