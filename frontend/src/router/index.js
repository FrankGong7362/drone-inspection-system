import { createRouter, createWebHistory } from 'vue-router'
import TaskList from '../views/TaskList.vue'
import PlanView from '../views/PlanView.vue'

const routes = [
  { path: '/', redirect: '/tasks' },
  { path: '/tasks', name: 'TaskList', component: TaskList },
  { path: '/map', name: 'PlanView', component: PlanView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
