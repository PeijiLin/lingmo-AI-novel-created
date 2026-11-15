<template>
  <div class="library-container">
    <div class="library-header">
      <h1>作品库</h1>
      <button @click="createNewWork" class="create-btn">+ 新建作品</button>
    </div>

    <div class="library-filters">
      <div class="filter-group">
        <label for="status-filter">状态：</label>
        <select id="status-filter" v-model="statusFilter">
          <option value="all">全部</option>
          <option value="0">已发布</option>
          <option value="1">已完结</option>
        </select>
      </div>

      <div class="filter-group">
        <label for="category-filter">分类：</label>
        <select id="category-filter" v-model="categoryFilter">
          <option value="all">全部</option>
          <option :value="item.name" v-for="item in getCategoryName()" :key="item.id">{{item.name}}</option>
        </select>
      </div>

      <div class="search-box">
        <input
          type="text"
          placeholder="搜索作品..."
          v-model="searchQuery"
          @input="handleSearch"
        />
      </div>
    </div>

    <div class="works-grid">
      <div
        v-for="work in filteredWorks"
        :key="work.id"
        class="work-card"
        @click="openWork(work)"
      >
        <div class="work-cover">
          <div class="cover-initial">{{ work.bookName?.charAt(0) }}</div>
        </div>
        <div class="work-info">
          <h3 class="work-title">{{ work.bookName }}</h3>
          <p class="work-category">{{ work.categoryName }}</p>
          <div class="work-meta">
            <span class="work-status">{{ getStatusText(work.bookStatus) }}</span>
            <span class="work-chapters">{{ work.visitCount }}章</span>
            <span class="work-words">{{ work.wordCount }}字</span>
          </div>
          <div class="work-tags">
            <span
              v-for="tag in work.tags"
              :key="tag"
              class="tag"
            >
              {{ tag }}
            </span>
            <span v-if="work.tags.length > 3" class="tag more">...</span>
          </div>
          <div class="work-actions">
            <button @click.stop="editWork(work)">编辑</button>
            <button @click.stop="publishWork(work)" v-if="work.bookStatus === 1">发布</button>
            <button @click.stop="deleteWork(work)">删除</button>
          </div>
        </div>
      </div>

      <div v-if="filteredWorks.length === 0" class="no-works">
        <p>暂无作品</p>
      </div>
    </div>

    <div class="pagination" v-if="totalPages > 1">
      <button
        @click="prevPage"
        :disabled="currentPage === 1"
        class="page-btn"
      >
        上一页
      </button>

      <span class="page-info">
        第 {{ currentPage }} 页，共 {{ totalPages }} 页
      </span>

      <button
        @click="nextPage"
        :disabled="currentPage === totalPages"
        class="page-btn"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import { useRouter } from 'vue-router'
import {
  type BookCategoryVo,
  type BookInfoListVo,
  CreationBookControllerService,
  ReadingControllerService
} from "@/api";
import {useUserStore} from "@/stores/user.ts";
import {setAuthorId} from "@/utils/token.ts";

const router = useRouter()

/**
 * 筛选和搜索状态
 */
const statusFilter = ref('all')
const categoryFilter = ref('all')
const searchQuery = ref('')

/**
 * 分页状态
 */
const currentPage = ref(1)
const itemsPerPage = ref(12)
const user = useUserStore()
const userId = ref()
userId.value = user.userId

/**
 * 模拟作品数据
 */

/**
 * 根据用户id查询创作的作品
 */
const categories = ref<Record<string, Array<BookCategoryVo>>>({})
const works = ref<BookInfoListVo[]>([])

/**
 * 获取书籍分类数据
 * @returns {Promise<void>} 无返回值
 */
const getCategories = async () => {
  const res = await ReadingControllerService.queryBookCategories()
  if (res.code === 200) {
    if (res.data) {
      categories.value = res.data
    }
  } else {
    alert("请求分类数据失败")
  }
}

/**
 * 获取作者创作的书籍信息列表
 * @returns {Promise<void>} 无返回值
 */
const getBookInfos = async () => {
  const res = await CreationBookControllerService.queryCreateBookListByAuthorId(userId.value)
  if (res.code === 200) {
    if (res.data) {
      works.value = res.data
      setAuthorId(res.data[0]?.authorId)
    }
  } else {
    console.log("请求书籍失败")
  }
}

onMounted(async () => {
  await getCategories()
  await getBookInfos()
})

/**
 * 计算过滤后的工作列表（支持状态、分类、搜索关键词过滤及分页）
 * @returns {Array} 过滤并分页后的作品数组
 */
const filteredWorks = computed(() => {
  let result = works.value

  // 状态过滤
  if (statusFilter.value !== 'all') {
    result = result.filter(work => work.bookStatus?.toString() === statusFilter.value)
  }

  // 分类过滤
  if (categoryFilter.value !== 'all') {
    result = result.filter(work => work.categoryName === categoryFilter.value)
  }

  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(work =>
      work.bookName?.toLowerCase().includes(query) ||
      work.tags?.some(tag => tag.toLowerCase().includes(query))
    )
  }

  // 分页处理
  const start = (currentPage.value - 1) * itemsPerPage.value
  const end = start + itemsPerPage.value

  return result.slice(start, end)
})

/**
 * 计算总页数（根据当前过滤条件动态计算）
 * @returns {Number} 总页数
 */
const totalPages = computed(() => {
  let count = works.value.length

  // 应用过滤器
  if (statusFilter.value !== 'all') {
    count = works.value.filter(work => work.bookStatus?.toString() === statusFilter.value).length
  }

  if (categoryFilter.value !== 'all') {
    count = works.value.filter(work => work.categoryName === categoryFilter.value).length
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    count = works.value.filter(work =>
      work.bookName?.toLowerCase().includes(query) ||
      work.tags?.some(tag => tag.toLowerCase().includes(query))
    ).length
  }

  return Math.ceil(count / itemsPerPage.value)
})


/**
 * 获取所有分类名称（合并男频和女频）
 * @returns {Array<BookCategoryVo>} 合并后的分类数组
 */
const getCategoryName = () => {
  const maleCategories = categories.value['男频'] || []
  const femaleCategories = categories.value['女频'] || []

  return [...maleCategories, ...femaleCategories]
}

/**
 * 获取状态文本描述
 * @param {string} status - 状态标识
 * @returns {string} 对应的状态中文描述
 */
const getStatusText = (status: number) => {
  const statuses: Record<string, string> = {
    0: '草稿',
    1: '已发布',
  }
  return statuses[status] || status
}

/**
 * 处理搜索事件（重置到第一页）
 * @returns {void}
 */
const handleSearch = () => {
  // 重置到第一页
  currentPage.value = 1
}

/**
 * 上一页
 * @returns {void}
 */
const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
  }
}

/**
 * 下一页
 * @returns {void}
 */
const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
  }
}

/**
 * 创建新作品
 * @returns {void}
 */
const createNewWork = () => {
  // 创建新作品
  router.push('/creator')
}

/**
 * 打开作品详情或编辑页面
 * @param {any} work - 要打开的作品对象
 * @returns {void}
 */
const openWork = (work: any) => {
  // 打开作品详情或编辑页面
  router.push(`/creator/${work.id}`)
}

/**
 * 编辑作品
 * @param {any} work - 要编辑的作品对象
 * @returns {void}
 */
const editWork = (work: BookInfoListVo) => {
  // 编辑作品
  router.push(`/creator/${work.id}`)
}

/**
 * 发布作品
 * @param {any} work - 要发布的的作品对象
 * @returns {void}
 */
const publishWork = (work: BookInfoListVo) => {
  // 发布作品
  work.bookStatus = 1
  alert(`作品《${work.bookName}》已发布`)
}

/**
 * 删除作品
 * @param {any} work - 要删除的作品对象
 * @returns {void}
 */
const deleteWork = (work: BookInfoListVo) => {
  // 删除作品
  if (confirm(`确定要删除作品《${work.bookName}》吗？此操作不可恢复。`)) {
    works.value = works.value.filter(w => w.id !== work.id)
  }
}

</script>

<style scoped>
.library-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
  padding: 1rem;
}

.library-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.library-header h1 {
  margin: 0;
  color: #333;
}

.create-btn {
  padding: 0.75rem 1.5rem;
  background-color: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
  font-size: 1rem;
}

.library-filters {
  display: flex;
  gap: 1.5rem;
  padding: 1rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
}

.filter-group label {
  margin-right: 0.5rem;
  color: #666;
}

.filter-group select {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.search-box input {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  min-width: 200px;
}

.works-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
  overflow-y: auto;
  padding: 0.5rem;
}

.work-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.work-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.15);
}

.work-cover {
  height: 150px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-initial {
  font-size: 3rem;
  color: white;
  font-weight: bold;
}

.work-info {
  padding: 1.5rem;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.work-title {
  margin: 0 0 0.5rem 0;
  font-size: 1.25rem;
  color: #333;
}

.work-category {
  margin: 0 0 1rem 0;
  color: #409eff;
  font-size: 0.9rem;
}

.work-meta {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.work-status {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: bold;
}

.work-status.draft {
  background-color: #fef0f0;
  color: #f56c6c;
}

.work-status.published {
  background-color: #ecf5ff;
  color: #409eff;
}

.work-status.finished {
  background-color: #f0f9eb;
  color: #67c23a;
}

.work-chapters,
.work-words {
  color: #999;
}

.work-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
  flex: 1;
}

.tag {
  padding: 0.25rem 0.5rem;
  background-color: #f0f0f0;
  border-radius: 12px;
  font-size: 0.75rem;
  color: #666;
}

.tag.more {
  background-color: transparent;
  color: #999;
}

.work-actions {
  display: flex;
  gap: 0.5rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.work-actions button {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  flex: 1;
}

.work-actions button:first-child {
  background-color: #f0f0f0;
  color: #666;
}

.work-actions button:nth-child(2) {
  background-color: #409eff;
  color: white;
}

.work-actions button:last-child {
  background-color: #fef0f0;
  color: #f56c6c;
}

.no-works {
  grid-column: 1 / -1;
  text-align: center;
  padding: 3rem;
  color: #999;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
}

.page-btn {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  color: #666;
}

@media (max-width: 768px) {
  .library-filters {
    flex-direction: column;
    gap: 1rem;
  }

  .works-grid {
    grid-template-columns: 1fr;
  }

  .work-card {
    flex-direction: row;
  }

  .work-cover {
    width: 120px;
    height: auto;
  }
}
</style>
