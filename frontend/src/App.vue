<template>
  <el-container class="layout">
    <el-header class="header">
      无人机巡检任务管理与路径规划系统
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
</script>

<style>
.layout {
  height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  background: #0f3d91;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
</style>
