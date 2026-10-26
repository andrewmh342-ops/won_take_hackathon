// 담당: 역할 1 — 개발 서버
import { defineConfig } from 'vite';
export default defineConfig({ server: { proxy: { '/api': 'http://localhost:8080' } } });
