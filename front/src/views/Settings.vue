<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const userStore = useUserStore()

const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)
const showPasswordForm = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  changingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码修改成功')
    showPasswordForm.value = false
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch {
    // handled by interceptor
  } finally {
    changingPassword.value = false
  }
}

function handleCancelPassword() {
  showPasswordForm.value = false
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}
</script>

<template>
  <div class="settings">
    <el-page-header @back="$router.back()">
      <template #content>
        <span>个人设置</span>
      </template>
    </el-page-header>

    <div class="settings-content">
      <!-- 账户信息 -->
      <el-card class="settings-card">
        <template #header>
          <div class="card-header">
            <el-icon :size="18"><User /></el-icon>
            <span>账户信息</span>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">
            {{ userStore.userInfo?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="用户ID">
            {{ userStore.userInfo?.id || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 修改密码 -->
      <el-card class="settings-card">
        <template #header>
          <div class="card-header">
            <el-icon :size="18"><Lock /></el-icon>
            <span>修改密码</span>
          </div>
        </template>

        <template v-if="!showPasswordForm">
          <p class="hint-text">定期修改密码可以提高账户安全性</p>
          <el-button type="primary" @click="showPasswordForm = true">修改密码</el-button>
        </template>

        <el-form
          v-else
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-width="100px"
          style="max-width: 420px"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              show-password
              placeholder="请输入当前密码"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              placeholder="请输入新密码（不少于6位）"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="changingPassword"
              @click="handleChangePassword"
            >
              确认修改
            </el-button>
            <el-button @click="handleCancelPassword">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.settings-content {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 640px;
}

.settings-card :deep(.el-card__header) {
  padding: 14px 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--color-text);
}

.hint-text {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin: 0 0 16px;
}
</style>
