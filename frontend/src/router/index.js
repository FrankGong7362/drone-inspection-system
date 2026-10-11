import { createRouter, createWebHistory } from 'vue-router'
import TaskList from '../views/TaskList.vue'
import PlanView from '../views/PlanView.vue'
import LoginView from '../views/LoginView.vue'

const routes = [
  { path: '/', redirect: '/tasks' },
  { path: '/login', name: 'Login', component: LoginView },
  { path: '/tasks', name: 'TaskList', component: TaskList },
  { path: '/map', name: 'PlanView', component: PlanView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录一律跳登录页；已登录访问登录页跳任务列表
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/tasks'
})

export default router
