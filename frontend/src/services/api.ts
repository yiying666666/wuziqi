import type { LeaderboardItem, Profile, User } from "../types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:3000";

const request = async <T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> => {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {}),
    },
  });

  if (!response.ok) {
    const data = (await response.json().catch(() => null)) as { message?: string } | null;
    throw new Error(data?.message ?? `Request failed: ${response.status}`);
  }

  return (await response.json()) as T;
};

export const authApi = {
  register: (username: string, password: string) =>
    request<{ token: string; user: User }>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  login: (username: string, password: string) =>
    request<{ token: string; user: User }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
};

export const profileApi = {
  me: (token: string) => request<Profile>("/me/profile", {}, token),
  leaderboard: (limit = 50) =>
    request<LeaderboardItem[]>(`/leaderboard?limit=${limit}`),
};
