import client from './client';

export const authApi = {
  login(data) {
    return client.post('/auth/login', data);
  },
  register(data) {
    return client.post('/auth/register', data);
  },
  profile() {
    return client.get('/auth/me');
  }
};

export const settingApi = {
  get() {
    return client.get('/settings');
  },
  update(data) {
    return client.put('/settings', data);
  }
};

export const reservationApi = {
  history() {
    return client.get('/reservations/history');
  },
  manual(data) {
    return client.post('/reservations/manual', data);
  },
  cancel(data) {
    return client.post('/reservations/cancel', data);
  },
  live() {
    return client.get('/reservations/live');
  }
};

export const trafficApi = {
  latest() {
    return client.get('/traffic/latest');
  },
  recent(hours = 24) {
    return client.get('/traffic/recent', { params: { hours } });
  },
  exportCsv() {
    return client.get('/traffic/export', { responseType: 'blob' });
  }
};

export const adminApi = {
  inviteCodes() {
    return client.get('/admin/invite-codes');
  },
  createInviteCodes(count) {
    return client.post('/admin/invite-codes', { count });
  },
  deleteInviteCode(code) {
    return client.delete(`/admin/invite-codes/${code}`);
  },
  systemSettings() {
    return client.get('/admin/system-settings');
  },
  updateSystemSettings(settings) {
    return client.put('/admin/system-settings', settings);
  },
  logs(limit = 50) {
    return client.get('/admin/logs', { params: { limit } });
  },
  users() {
    return client.get('/admin/users');
  }
};
