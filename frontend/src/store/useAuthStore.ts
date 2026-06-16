import { create } from 'zustand';

interface User {
  userId: string;
  username: string;
  nickname: string;
  isAdmin: boolean;
}

interface MenuNode {
  id: string;
  name: string;
  type: string;
  path?: string;
  icon?: string;
  permissionCode?: string;
  sortOrder?: number;
  visible?: number;
  children?: MenuNode[];
}

interface AuthState {
  token: string | null;
  user: User | null;
  isLoggedIn: boolean;
  permissions: string[];
  menus: MenuNode[];
  setAuth: (token: string, user: User, permissions?: string[], menus?: MenuNode[]) => void;
  setMenus: (menus: MenuNode[]) => void;
  logout: () => void;
  loadFromStorage: () => void;
  hasPermission: (permissionCode: string) => boolean;
  hasAnyPermission: (...permissionCodes: string[]) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: null,
  user: null,
  isLoggedIn: false,
  permissions: [],
  menus: [],

  setAuth: (token: string, user: User, permissions: string[] = [], menus: MenuNode[] = []) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
    localStorage.setItem('permissions', JSON.stringify(permissions));
    localStorage.setItem('menus', JSON.stringify(menus));
    set({ token, user, isLoggedIn: true, permissions, menus });
  },

  setMenus: (menus: MenuNode[]) => {
    localStorage.setItem('menus', JSON.stringify(menus));
    set({ menus });
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('permissions');
    localStorage.removeItem('menus');
    set({ token: null, user: null, isLoggedIn: false, permissions: [], menus: [] });
  },

  loadFromStorage: () => {
    const token = localStorage.getItem('token');
    const userStr = localStorage.getItem('user');
    const permsStr = localStorage.getItem('permissions');
    const menusStr = localStorage.getItem('menus');
    const permissions = permsStr ? JSON.parse(permsStr) : [];
    const menus = menusStr ? JSON.parse(menusStr) : [];
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr);
        set({ token, user, isLoggedIn: true, permissions, menus });
      } catch {
        set({ token: null, user: null, isLoggedIn: false, permissions: [], menus: [] });
      }
    }
  },

  hasPermission: (permissionCode: string) => {
    const { permissions, user } = get();
    if (user?.isAdmin) return true;
    return permissions.includes(permissionCode);
  },

  hasAnyPermission: (...permissionCodes: string[]) => {
    const { permissions, user } = get();
    if (user?.isAdmin) return true;
    return permissionCodes.some((code) => permissions.includes(code));
  },
}));
