<template>
  <svg :width="size" :height="size" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
    <!-- 书本底部 -->
    <rect x="6" y="10" width="36" height="30" rx="4" fill="#FFE5A0" stroke="#E8C878" stroke-width="1.5"/>
    <!-- 书本封面 -->
    <rect x="8" y="8" width="32" height="28" rx="3" fill="#FFFFFF" stroke="#FF9BB3" stroke-width="1.5"/>
    <!-- 书脊 -->
    <line x1="24" y1="8" x2="24" y2="36" stroke="#FFCDD9" stroke-width="1.5"/>
    <!-- 左页漫画线条 - 小角色轮廓 (Chiikawa风格小圆脸) -->
    <circle cx="17" cy="20" r="5" fill="#FFF5EE" stroke="#FFB6C1" stroke-width="1.2"/>
    <!-- 小耳朵 -->
    <ellipse cx="14" cy="16.5" rx="1.5" ry="2" fill="#FFF5EE" stroke="#FFB6C1" stroke-width="1"/>
    <ellipse cx="20" cy="16.5" rx="1.5" ry="2" fill="#FFF5EE" stroke="#FFB6C1" stroke-width="1"/>
    <!-- 小眼睛 -->
    <circle cx="15.5" cy="19.5" r="0.8" fill="#3D3D3D"/>
    <circle cx="18.5" cy="19.5" r="0.8" fill="#3D3D3D"/>
    <!-- 小嘴 -->
    <ellipse cx="17" cy="21.5" rx="1" ry="0.5" fill="#FF9BB3"/>
    <!-- 腮红 -->
    <circle cx="14.5" cy="21" r="1.2" fill="#FFCDD9" opacity="0.6"/>
    <circle cx="19.5" cy="21" r="1.2" fill="#FFCDD9" opacity="0.6"/>
    <!-- 右页文字线条 -->
    <rect x="27" y="14" width="10" height="1.5" rx="0.75" fill="#FFCDD9"/>
    <rect x="27" y="18" width="8" height="1.5" rx="0.75" fill="#FFE5A0"/>
    <rect x="27" y="22" width="10" height="1.5" rx="0.75" fill="#FFCDD9"/>
    <rect x="27" y="26" width="6" height="1.5" rx="0.75" fill="#FFE5A0"/>
    <!-- 左页对话框 -->
    <rect x="11" y="27" width="10" height="5" rx="2" fill="#F0F8FF" stroke="#7EC8E3" stroke-width="0.8"/>
    <text x="13" y="31" font-size="4" fill="#7EC8E3" font-weight="600">あ→A</text>
    <!-- 小星星装饰 -->
    <path d="M40 6l1 2 2 1-2 1-1 2-1-2-2-1 2-1z" fill="#FFE5A0"/>
    <path d="M8 4l0.7 1.3 1.3 0.7-1.3 0.7L8 8l-0.7-1.3L6 6l1.3-0.7z" fill="#FF9BB3" opacity="0.6"/>
    <path d="M42 38l0.7 1.3 1.3 0.7-1.3 0.7L42 42l-0.7-1.3L40 40l1.3-0.7z" fill="#7EC8E3" opacity="0.6"/>
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 36 })
</script>
