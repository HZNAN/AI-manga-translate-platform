<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { register } from '@/api/auth'
import type { FormInstance, FormRules } from 'element-plus'
import LogoIcon from '@/components/icons/LogoIcon.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({
  username: '',
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.value.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await register({
      username: form.value.username,
      password: form.value.password,
    })
    userStore.setToken(res.data.token)
    userStore.setUserInfo(res.data.user)
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-card">
      <div class="register-brand">
        <LogoIcon :size="52" />
        <h1 class="register-title">AI漫画翻译</h1>
        <p class="register-subtitle">创建新账户</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleRegister">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="register-btn" native-type="submit">
            注册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="register-footer">
        已有账户？<router-link to="/login">去登录</router-link>
      </div>
    </div>

    <div class="deco deco-1"></div>
    <div class="deco deco-2"></div>
    <div class="deco deco-3"></div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-bg);
  position: relative;
  overflow: hidden;
}

.register-card {
  width: 400px;
  padding: 44px 40px 36px;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border-light);
  position: relative;
  z-index: 1;
}

.register-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
}

.register-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin-top: 12px;
  letter-spacing: 1px;
}

.register-subtitle {
  color: var(--color-text-secondary);
  margin-top: 4px;
  font-size: 14px;
}

.register-btn {
  width: 100%;
  border-radius: var(--radius-sm);
  height: 42px;
  font-size: 15px;
  font-weight: 600;
}

.register-footer {
  text-align: center;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.register-footer a {
  color: var(--color-primary);
  font-weight: 500;
}

.register-footer a:hover {
  color: var(--color-primary-dark);
}

.deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.deco-1 {
  width: 320px;
  height: 320px;
  background: var(--color-primary-light);
  opacity: 0.25;
  top: -100px;
  left: -80px;
}

.deco-2 {
  width: 200px;
  height: 200px;
  background: var(--color-secondary);
  opacity: 0.2;
  bottom: -60px;
  right: -40px;
}

.deco-3 {
  width: 120px;
  height: 120px;
  background: var(--color-accent-light);
  opacity: 0.25;
  bottom: 30%;
  right: 10%;
}
</style>
