// src/store/user.ts
import { defineStore } from 'pinia'
import {getAccessToken, getUserId, clearTokens, setTokens, getAuthor} from '@/utils/token.ts'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken() || '',
    userId: getUserId() || null,
    isLoggedIn: !!getAccessToken(),
    authorId: getAuthor()
  }),


  getters: {
    isAuthed: (state) => state.isLoggedIn,
  },


  actions: {
    login(data: { access_token: string; refresh_token: string; userId: number }) {
      this.accessToken = data.access_token
      this.userId = data.userId
      this.isLoggedIn = true
      // 同步到 localStorage
      setTokens(data)
    },

    logout() {
      this.accessToken = ''
      this.userId = null
      this.isLoggedIn = false
      clearTokens()
    },
  },
})
