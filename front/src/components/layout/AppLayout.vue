<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  ElContainer,
  ElHeader,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElIcon,
  ElAvatar,
} from 'element-plus'
import {
  House,
  Setting,
  Clock,
  User,
  SwitchButton,
  Cpu,
} from '@element-plus/icons-vue'
import LogoIcon from '@/components/icons/LogoIcon.vue'

const router = useRouter()
const userStore = useUserStore()

function handleNav(index: string) {
  router.push(index)
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <ElContainer class="app-layout">
    <ElHeader class="app-header">
      <div class="header-left">
        <div class="logo" @click="router.push('/')">
          <LogoIcon :size="32" />
          <span class="logo-text">AI漫画翻译</span>
        </div>
        <ElMenu
          mode="horizontal"
          :default-active="$route.path"
          :ellipsis="false"
          class="header-nav"
          @select="handleNav"
        >
          <ElMenuItem index="/">
            <ElIcon><House /></ElIcon>
            <span>书架</span>
          </ElMenuItem>
          <ElMenuItem index="/history">
            <ElIcon><Clock /></ElIcon>
            <span>翻译历史</span>
          </ElMenuItem>
          <ElMenuItem index="/settings/configs">
            <ElIcon><Setting /></ElIcon>
            <span>翻译配置</span>
          </ElMenuItem>
          <ElMenuItem index="/settings/llm-configs">
            <ElIcon><Cpu /></ElIcon>
            <span>模型管理</span>
          </ElMenuItem>
        </ElMenu>
      </div>
      <div class="header-right">
        <ElDropdown v-if="userStore.isLoggedIn" trigger="click">
          <div class="user-info">
            <ElAvatar :size="30" :src="userStore.userInfo?.avatarUrl" class="user-avatar">
              <ElIcon><User /></ElIcon>
            </ElAvatar>
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
          </div>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem @click="router.push('/settings')">
                <ElIcon><Setting /></ElIcon>
                个人设置
              </ElDropdownItem>
              <ElDropdownItem divided @click="handleLogout">
                <ElIcon><SwitchButton /></ElIcon>
                退出登录
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>
        <template v-else>
          <el-button type="primary" text @click="router.push('/login')">登录</el-button>
          <el-button type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </ElHeader>
    <ElMain class="app-main">
      <router-view />
    </ElMain>
  </ElContainer>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  padding: 0 28px;
  height: 56px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 28px;
}

.logo {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  transition: opacity 0.2s;
}

.logo:hover {
  opacity: 0.8;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.header-nav {
  border-bottom: none !important;
  background: transparent !important;
}

.header-nav .el-menu-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: var(--color-bg-hover);
}

.user-avatar {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
}

.username {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.app-main {
  padding: 24px 28px;
  background-color: var(--color-bg);
  min-height: calc(100vh - 56px);
}
</style>
