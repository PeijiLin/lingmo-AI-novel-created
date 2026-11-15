<template>
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-semibold text-gray-800">
        我的知识库
      </h1>
      <el-button type="primary" :icon="Plus" @click="handleOpenCreateDialog">
        创建新知识库
      </el-button>
    </div>

    <div v-if="knowledgeBases.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <el-card
        v-for="kb in knowledgeBases"
        :key="kb.id"
        shadow="hover"
      >
        <template #header>
          <div class="flex justify-between items-center">
            <span class="font-bold text-lg text-blue-600">{{ kb.novelTitle }}</span>
            <el-tag :type="kb.isSynced ? 'success' : 'info'">
              {{ kb.isSynced ? 'AI 已同步' : '待同步' }}
            </el-tag>
          </div>
        </template>

        <div class="text-sm text-gray-600 space-y-2 mb-4">
          <div>
            <el-icon class="mr-1 align-text-bottom"><User /></el-icon>
            <span>角色条目：</span>
            <span class="font-medium">{{ kb.stats.characters }} 个</span>
          </div>
          <div>
            <el-icon class="mr-1 align-text-bottom"><MapLocation /></el-icon>
            <span>世界设定：</span>
            <span class="font-medium">{{ kb.stats.settings }} 条</span>
          </div>
          <div>
            <el-icon class="mr-1 align-text-bottom"><CollectionTag /></el-icon>
            <span>情节条目：</span>
            <span class="font-medium">{{ kb.stats.plots }} 条</span>
          </div>
          <div class="pt-2">
            <el-icon class="mr-1 align-text-bottom"><Timer /></el-icon>
            <span>最后更新：</span>
            <span class="font-medium">{{ kb.lastUpdated }}</span>
          </div>
        </div>

        <template #footer>
          <div class="flex justify-end space-x-2">
            <el-button
              type="primary"
              :icon="Edit"
              @click="handleManage(kb.id)"
            >
              管理
            </el-button>
            <el-button
              type="danger"
              :icon="Delete"
              text
              @click="handleDelete(kb.id, kb.novelTitle)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-card>
    </div>

    <el-empty v-else description="暂无知识库，快去创建一个吧！" />

    <el-dialog
      v-model="createDialogVisible"
      title="创建新知识库"
      width="500px"
    >
      <el-form :model="createForm" ref="createFormRef" label-position="top">
        <el-form-item
          label="选择关联小说"
          prop="novelId"
          required
        >
          <el-select
            v-model="createForm.novelId"
            placeholder="选择一本尚未建立知识库的小说"
            class="w-full"
            filterable
          >
            <el-option
              v-for="novel in availableNovels"
              :key="novel.id"
              :label="novel.title"
              :value="novel.id"
            />
          </el-select>
        </el-form-item>
        <p class="text-sm text-gray-500">
          AI 知识库将与您选择的小说绑定，用于提供专属的写作建议和续写。
        </p>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleCreateKB">
            立即创建
          </el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Plus,
  Edit,
  Delete,
  User,
  MapLocation,
  CollectionTag,
  Timer
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

// --- 响应式数据 ---

// 知识库列表 (应从 API 获取)
const knowledgeBases = ref([
  {
    id: 'kb-001',
    novelId: 'novel-123',
    novelTitle: '艾琳的日记',
    stats: { characters: 2, settings: 3, plots: 1 },
    lastUpdated: '2025-11-26 14:30',
    isSynced: true
  },
  {
    id: 'kb-002',
    novelId: 'novel-124',
    novelTitle: '九州·缥缈录',
    stats: { characters: 15, settings: 22, plots: 5 },
    lastUpdated: '2025-11-25 09:15',
    isSynced: false
  },
])

// 创建弹窗
const createDialogVisible = ref(false)
const createFormRef = ref(null)
const createForm = ref({
  novelId: null
})

// 可用于创建知识库的小说列表 (应从 API 获取)
// 理论上这里只应显示 *还没有* 知识库的小说
const availableNovels = ref([
  { id: 'novel-125', title: '我的新小说' },
  { id: 'novel-126', title: '都市奇缘' },
  // '艾琳的日记' 和 '九州' 不应出现在这里，因为它们已经有KB了
])


// --- 方法 ---

// 打开创建弹窗
const handleOpenCreateDialog = () => {
  // 可以在这里重新从 API 获取最新的 availableNovels
  createForm.value.novelId = null
  createDialogVisible.value = true
}

// 确认创建
const handleCreateKB = async () => {
  if (!createForm.value.novelId) {
    ElMessage.error('请选择要关联的小说')
    return
  }

  // --- 模拟 API 调用 ---
  console.log('正在为小说 ID 创建知识库:', createForm.value.novelId)

  // 假设创建成功，后端返回了新的 KB 信息
  const selectedNovel = availableNovels.value.find(n => n.id === createForm.value.novelId)
  const newKB = {
    id: `kb-${Date.now()}`, // 临时ID
    novelId: selectedNovel.id,
    novelTitle: selectedNovel.title,
    stats: { characters: 0, settings: 0, plots: 0 },
    lastUpdated: new Date().toLocaleString(),
    isSynced: false
  }

  knowledgeBases.value.push(newKB)
  // 从 availableNovels 列表中移除
  availableNovels.value = availableNovels.value.filter(n => n.id !== selectedNovel.id)

  ElMessage.success('知识库创建成功！')
  createDialogVisible.value = false

  // 自动跳转到编辑页面
  router.push(`/knowledgebase/${newKB.id}`)
}

// 管理 (跳转)
const handleManage = (kbId) => {
  // 跳转到我们昨天编写的那个编辑器页面
  router.push(`/knowledgebase/${kbId}`)
}

// 删除
const handleDelete = (kbId, novelTitle) => {
  ElMessageBox.confirm(
    `确定要删除《${novelTitle}》的专属知识库吗？<br/><strong>此操作不可撤销</strong>，所有相关设定将会丢失。`,
    '严重警告',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      dangerouslyUseHTMLString: true, // 允许 <br/> 和 <strong>
    }
  ).then(() => {
    // --- 模拟 API 调用 ---
    console.log('删除知识库:', kbId)
    knowledgeBases.value = knowledgeBases.value.filter(kb => kb.id !== kbId)
    ElMessage.success('知识库已删除')

    // (可选) 应该把这本小说重新加回到 availableNovels 列表

  }).catch(() => {
    // 用户取消
  })
}

</script>

<style scoped>
/* Tailwind 提供了大部分样式 */
.el-card {
  transition: all 0.3s ease;
}
.el-card:hover {
  transform: translateY(-5px);
}
</style>
