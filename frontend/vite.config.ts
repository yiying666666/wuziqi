import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const backend = "http://127.0.0.1:3000";

export default defineConfig({
  plugins: [vue()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    allowedHosts: true,
    proxy: {
      "/auth": { target: backend, changeOrigin: true },
      "/me": { target: backend, changeOrigin: true },
      "/leaderboard": { target: backend, changeOrigin: true },
      "/rooms": { target: backend, changeOrigin: true },
      "/ai": { target: backend, changeOrigin: true },
      "/health": { target: backend, changeOrigin: true },
      "/ws": { target: backend, ws: true, changeOrigin: true },
    },
  },
});
