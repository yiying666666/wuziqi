import { reactive } from "vue";
import type { User } from "../types";

interface SessionState {
  token: string | null;
  user: User | null;
}

const initialToken = window.localStorage.getItem("wuziqi_token");
const initialUser = window.localStorage.getItem("wuziqi_user");

const state = reactive<SessionState>({
  token: initialToken,
  user: initialUser ? (JSON.parse(initialUser) as User) : null,
});

export const useSession = () => {
  const setSession = (token: string, user: User): void => {
    state.token = token;
    state.user = user;
    window.localStorage.setItem("wuziqi_token", token);
    window.localStorage.setItem("wuziqi_user", JSON.stringify(user));
  };

  const clearSession = (): void => {
    state.token = null;
    state.user = null;
    window.localStorage.removeItem("wuziqi_token");
    window.localStorage.removeItem("wuziqi_user");
  };

  return { state, setSession, clearSession };
};
