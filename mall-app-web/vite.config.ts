import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// https://vitejs.dev/config/
export default defineConfig({
  build: {
    // 开发阶段启用源码映射：https://uniapp.dcloud.net.cn/tutorial/migration-to-vue3.html#需主动开启-sourcemap
    sourcemap: process.env.NODE_ENV === 'development',
  },
  plugins: [uni()],
  server: {
    // 开发环境把 /api 代理到 mall-portal，避免浏览器跨域预检
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, ''),
      },
    },
  },
})
