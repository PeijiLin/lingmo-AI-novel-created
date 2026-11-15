// 用户登录态（Pinia）
import { defineStore } from 'pinia'
import {
  clearTokens,
  getAccessToken,
  getAuthorId,
  getUserId,
  isAuthenticated,
  setAuthorId,
  setTokens,
} from '@/utils/token'
import type { TokenPair } from '@/utils/token'

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: getAccessToken() || '',
    userId: getUserId(),
    isLoggedIn: isAuthenticated(),
    authorId: getAuthorId(),
  }),

  getters: {
    isAuthed: (state) => state.isLoggedIn,
  },

  actions: {
    login(data: TokenPair) {
      this.accessToken = data.access_token
      this.userId = data.userId
      this.isLoggedIn = true
      setTokens(data)
    },

    setAuthor(authorId: number | null) {
      this.authorId = authorId
      setAuthorId(authorId)
    },

    logout() {
      this.accessToken = ''
      this.userId = null
      this.isLoggedIn = false
      this.authorId = null
      clearTokens()
    },
  },
})
