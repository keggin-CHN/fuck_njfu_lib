import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '../stores/user';

const Login = () => import('../views/LoginView.vue');
const Register = () => import('../views/RegisterView.vue');
const Dashboard = () => import('../views/DashboardView.vue');
const Reservations = () => import('../views/ReservationsView.vue');
const Settings = () => import('../views/SettingsView.vue');
const Traffic = () => import('../views/TrafficView.vue');
const Admin = () => import('../views/AdminView.vue');
const Layout = () => import('../components/AppLayout.vue');

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
      component: Layout,
      children: [
        {
          path: '/dashboard',
          name: 'dashboard',
          component: Dashboard,
          meta: { requiresAuth: true }
        },
        {
          path: '/reservations',
          name: 'reservations',
          component: Reservations,
          meta: { requiresAuth: true }
        },
        {
          path: '/settings',
          name: 'settings',
          component: Settings,
          meta: { requiresAuth: true }
        },
        {
          path: '/traffic',
          name: 'traffic',
          component: Traffic,
          meta: { requiresAuth: true }
        },
        {
          path: '/admin',
          name: 'admin',
          component: Admin,
          meta: { requiresAuth: true, admin: true }
        }
      ]
    },
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: { guest: true }
    },
    {
      path: '/register',
      name: 'register',
      component: Register,
      meta: { guest: true }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard'
    }
  ]
});

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore();
  if (!userStore.profile && userStore.token) {
    await userStore.fetchProfile();
  }

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    return next({ name: 'login', query: { redirect: to.fullPath } });
  }

  if (to.meta.admin && !userStore.isAdmin) {
    return next({ name: 'dashboard' });
  }

  if (to.meta.guest && userStore.isAuthenticated) {
    return next({ name: 'dashboard' });
  }

  next();
});

export default router;
