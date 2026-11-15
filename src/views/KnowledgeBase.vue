<template>
  <!-- 1. 根容器改为 Flex 布局 -->
  <div class="h-full flex bg-gray-50">

    <!-- 2. 主内容区域 - 使用 flex-1 占据剩余空间 -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Loading Overlay -->
      <!-- (注意：这里的结构也按之前建议优化过，避免重复属性) -->
      <div v-if="isLoading" class="absolute inset-0 z-50">
        <div
          v-loading="true"
          element-loading-text="正在加载知识库..."
          class="w-full h-full bg-white bg-opacity-75"
        ></div>
      </div>

      <!-- Main Content Area -->
      <div class="flex-1 p-6 overflow-y-auto">
        <div class="flex justify-between items-center mb-6">
          <h1 class="text-2xl font-semibold text-gray-800">
            知识库管理：{{ novelTitle }}
          </h1>
          <div>
            <!-- 3. AI Toggle Button - 放在这里 -->
            <el-button type="success" plain icon="MagicStick" @click="toggleAIDrawer" class="mr-2">
              AI 助手
            </el-button>
            <el-button type="primary" :icon="Check" @click="saveAll">
              保存全部更改
            </el-button>
          </div>
        </div>

        <el-tabs v-if="!isLoading" v-model="activeTab" type="border-card"
                 class="shadow-sm h-full flex flex-col">
          <!-- Characters Tab -->
          <el-tab-pane label="角色设定" name="characters" class="h-full">
            <template #label>
              <span class="flex items-center">
                <el-icon><User/></el-icon>
                <span class="ml-2">角色设定</span>
              </span>
            </template>

            <div class="mb-4">
              <el-button type="primary" :icon="Plus" @click="handleAddCharacter">
                添加新角色
              </el-button>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <el-card
                v-for="char in characterList"
                :key="char.id"
                shadow="hover"
              >
                <template #header>
                  <div class="flex justify-between items-center">
                    <span class="font-bold">{{ char.name }}</span>
                    <div>
                      <el-button type="primary" text size="small" :icon="Edit"
                                 @click="handleEditCharacter(char)"></el-button>
                      <el-button type="danger" text size="small" :icon="Delete"
                                 @click="handleDeleteCharacter(char.id)"></el-button>
                    </div>
                  </div>
                </template>
                <p class="text-sm text-gray-600 truncate" :title="char.description">
                  {{ char.description || '暂无描述' }}
                </p>
              </el-card>
            </div>

            <el-empty v-if="characterList.length === 0" description="暂无角色，点击上方按钮添加"/>

          </el-tab-pane>

          <!-- Setting Tab -->
          <!-- Setting Tab -->
          <el-tab-pane label="世界观/地点" name="setting">
            <template #label>
    <span class="flex items-center">
      <el-icon><MapLocation/></el-icon>
      <span class="ml-2">世界观/地点</span>
    </span>
            </template>

            <div class="mb-4">
              <el-button type="primary" :icon="Plus" @click="handleAddSettingEntry">
                添加新设定
              </el-button>
            </div>

            <!-- 设置条目列表 - 使用 v-for 为每个条目生成相同结构 -->
            <div v-if="settingEntries.length > 0" class="space-y-3">
              <div
                v-for="(entry, index) in settingEntries"
                :key="index"
              class="border border-gray-200 rounded-lg overflow-hidden shadow-sm hover:shadow-md transition-shadow"
              >
              <!-- 可编辑的标题栏 - 这个结构对所有条目都一样 -->
              <div class="px-4 py-3 bg-gray-50 border-b border-gray-200 flex justify-between items-center cursor-pointer"
                   @click="startEditingTitle(index)">
                <!-- 标题显示或输入框 -->
                <div v-if="!editingTitleIndex || editingTitleIndex !== index" class="font-medium text-gray-800 truncate max-w-full">
                  {{ entry.title }}
                </div>
                <el-input
                  v-else
                  v-model="entry.title"
                  size="small"
                  placeholder="请输入标题..."
                  @blur="endEditingTitle(index)"
                  @keyup.enter="endEditingTitle(index)"
                  @keyup.esc="cancelEditingTitle(index)"
                  class="w-full"
                />

                <!-- 编辑/保存/取消按钮组 -->
                <div v-if="editingTitleIndex === index" class="flex space-x-1">
                  <el-button size="small" type="success" @click="endEditingTitle(index)">保存</el-button>
                  <el-button size="small" type="danger" @click="cancelEditingTitle(index)">取消</el-button>
                </div>
                <div v-else class="text-gray-500 text-sm">
                  <el-button size="small" type="primary" plain @click.stop="startEditingTitle(index)">编辑</el-button>
                  <el-button size="small" type="danger" plain @click.stop="handleDeleteSettingEntry(index)" class="ml-1">
                    删除
                  </el-button>
                </div>
              </div>

              <!-- 内容区域 -->
              <div class="p-4">
                <el-input
                  v-model="entry.content"
                  type="textarea"
                  :rows="6"
                  placeholder="请输入详细设定..."
                  class="w-full"
                />
              </div>
            </div>
      </div>
      <el-empty v-else description="暂无设定，点击上方按钮添加"/>
      </el-tab-pane>

          <!-- Plot Tab -->
          <el-tab-pane label="情节/大纲" name="plot">
            <template #label>
              <span class="flex items-center">
                <el-icon><CollectionTag/></el-icon>
                <span class="ml-2">情节/大纲</span>
              </span>
            </template>
            <el-form label-position="top">
              <el-form-item label="核心大纲 (AI 将参考此大纲进行创作)">
                <el-input
                  v-model="plotOutline"
                  type="textarea"
                  :rows="15"
                  placeholder="请输入你的故事核心大纲、主要情节转折、背景传说等..."
                />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- Style Tab -->
          <el-tab-pane label="写作风格" name="style">
            <template #label>
              <span class="flex items-center">
                <el-icon><Brush/></el-icon>
                <span class="ml-2">写作风格</span>
              </span>
            </template>
            <el-form :model="writingStyle" label-width="100px" class="max-w-xl">
              <el-form-item label="写作基调">
                <el-select v-model="writingStyle.tone" placeholder="选择一个基调" multiple
                           filterable allow-create>
                  <el-option label="黑暗" value="dark"></el-option>
                  <el-option label="幽默" value="humorous"></el-option>
                  <el-option label="史诗" value="epic"></el-option>
                  <el-option label="悬疑" value="suspenseful"></el-option>
                  <el-option label="浪漫" value="romantic"></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="叙事视角">
                <el-radio-group v-model="writingStyle.perspective">
                  <el-radio label="first_person">第一人称 (我)</el-radio>
                  <el-radio label="third_person_limited">第三人称限定 (他/她)</el-radio>
                  <el-radio label="third_person_omniscient">第三人称全知</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="风格参考">
                <el-input
                  v-model="writingStyle.reference"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：文风简洁，多用短句。或：类似海明威的硬汉风格。"
                />
              </el-form-item>
              <el-form-item label="自定义指令">
                <el-input
                  v-model="writingStyle.customPrompt"
                  type="textarea"
                  :rows="5"
                  placeholder="给 AI 的永久性指令。例如：'永远不要描写主角的心理活动'，'侧重于环境描写'"
                />
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div> <!-- End of Main Content -->
    </div> <!-- End of Main Content Wrapper -->

    <!-- 4. 自定义 AI Sidebar - 使用 Flex 布局和动态类名 -->
    <div
      :class="[
        'h-full transition-all duration-300 ease-in-out flex flex-col border-l border-gray-200 bg-white flex-shrink-0',
        aiDrawerVisible ? 'w-[40%]' : 'w-0 overflow-hidden'
      ]"
    >
      <!-- AI Sidebar Header -->
      <div class="p-4 border-b border-gray-200 flex justify-between items-center flex-shrink-0">
        <h3 class="text-lg font-medium">AI 助手</h3>
        <el-button @click="toggleAIDrawer" type="danger" :icon="Close" circle size="small"/>
      </div>

      <!-- AI Sidebar Body & Footer - 可滚动区域 -->
      <div class="flex-1 flex flex-col min-h-0"> <!-- min-h-0 关键：允许内部滚动 -->
        <div class="flex-1 overflow-y-auto p-4">
          <el-alert
            v-if="aiError"
            :title="aiError"
            type="error"
            show-icon
            closable
            @close="aiError = ''"
            class="mb-4"
          />
          <el-alert
            v-if="aiResponse"
            title="AI 回复"
            type="success"
            :description="aiResponse"
            show-icon
            class="mb-4 whitespace-pre-wrap"
          />
          <div v-if="!aiResponse && !aiLoading && !aiError" class="text-gray-500 text-sm mb-4">
            输入提示词并点击“生成”，AI 会根据当前知识库内容为你提供建议。
          </div>
          <el-input
            v-model="aiInput"
            type="textarea"
            :rows="6"
            placeholder="请输入你的提示词，例如：根据大纲，帮我构思一下第一章的开头场景。"
            :disabled="aiLoading"
            class="mb-4"
          />
        </div>
        <div class="p-4 border-t border-gray-200 flex justify-end flex-shrink-0">
          <el-button
            type="primary"
            @click="handleGenerateWithAI"
            :loading="aiLoading"
            :disabled="!aiInput.trim() || aiLoading"
          >
            {{ aiLoading ? '生成中...' : '生成' }}
          </el-button>
        </div>
      </div>
    </div>
    <!-- End of Custom AI Sidebar -->

    <!-- 5. Character Drawer - 保持原样 -->
    <el-drawer
      v-model="characterDrawerVisible"
      :title="isEditing ? '编辑角色' : '添加新角色'"
      direction="rtl"
      size="40%"
    >
      <el-form :model="editingCharacter" label-position="top" class="p-4">
        <el-form-item label="角色姓名" required>
          <el-input v-model="editingCharacter.name"/>
        </el-form-item>
        <el-form-item label="角色别称/身份">
          <el-input v-model="editingCharacter.alias" placeholder="例如：'暗夜剑圣', '李家二小姐'"/>
        </el-form-item>
        <el-form-item label="角色简介/核心设定">
          <el-input
            v-model="editingCharacter.description"
            type="textarea"
            :rows="5"
            placeholder="AI 将重点参考此内容。例如：性格冷酷，目标是复仇，擅长使用冰系魔法。"
          />
        </el-form-item>
        <el-form-item label="详细背景故事">
          <el-input
            v-model="editingCharacter.backstory"
            type="textarea"
            :rows="8"
            placeholder="详细的背景故事、人际关系等..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="flex: auto">
          <el-button @click="characterDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveCharacter">
            保存角色
          </el-button>
        </div>
      </template>
    </el-drawer>
    <!-- End of Character Drawer -->

  </div> <!-- End of Root Flex Container -->
</template>

<script setup lang="ts">
import {ref, reactive, onMounted, computed} from 'vue'
import {
  Plus,
  Edit,
  Delete,
  Check,
  User,
  MapLocation,
  CollectionTag,
  Brush, Close// Import the icon
} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useRoute} from "vue-router"

const route = useRoute()
const id = route.params.id

const isLoading = ref(true)

const novelTitle = ref('')
const activeTab = ref('characters')

// 1. 角色列表
const characterList = ref<any[]>([])
const characterDrawerVisible = ref(false)
const isEditing = ref(false)
const editingCharacter = ref({
  id: null,
  name: '',
  alias: '',
  description: '',
  backstory: '',
})

// 2. 世界观/地点
const activeSettingEntry = ref(0)
const settingEntries = ref<any[]>([])

// 3. 情节大纲
const plotOutline = ref('')

// 4. 写作风格
const writingStyle = reactive({
  tone: [],
  perspective: 'third_person_limited',
  reference: '',
  customPrompt: ''
})

// --- AI Drawer State ---
const aiDrawerVisible = ref(false)
const aiInput = ref('')
const aiResponse = ref('')
const aiLoading = ref(false)
const aiError = ref('')

// --- Simulated AI Context (for demo purposes) ---
const aiContext = computed(() => {
  return `
小说名称: ${novelTitle.value}
核心大纲:
${plotOutline.value}

角色列表:
${characterList.value.map(c => `- ${c.name}: ${c.description}`).join('\n')}

世界观/地点:
${settingEntries.value.map(s => `${s.title}: ${s.content}`).join('\n\n')}

写作风格:
基调: ${Array.isArray(writingStyle.tone) ? writingStyle.tone.join(', ') : ''}
视角: ${writingStyle.perspective}
风格参考: ${writingStyle.reference}
自定义指令: ${writingStyle.customPrompt}
`.trim()
})
// 新增数据
const editingTitleIndex = ref<number | null>(null); // 记录当前正在编辑的标题索引

// 新增方法
// 开始编辑某个条目的标题
const startEditingTitle = (index: number) => {
  console.log('Start editing title at index:', index); // 添加此行
  editingTitleIndex.value = index;
};

// 结束编辑并保存
const endEditingTitle = (index: number) => {
  editingTitleIndex.value = null; // 结束编辑模式
};

// 取消编辑
const cancelEditingTitle = (index: number) => {
  console.log('Cancel editing title at index:', index); // 添加此行
  // 如果需要撤销更改，可以在这里恢复原始值
  // 例如：settingEntries[index].title = originalTitle;
  editingTitleIndex.value = null;
};

// 删除条目 - 确保它能正确工作
const handleDeleteSettingEntry = (index: number) => {
  ElMessageBox.confirm(
    '确定要删除这个设定吗？',
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      // 1. 检查是否正在删除当前正在编辑的条目
      if (editingTitleIndex.value === index) {
        editingTitleIndex.value = null; // 清理编辑状态
      }
      // 2. （可选）如果删除的是前面的条目，且正在编辑后面的条目，需要调整索引
      //    但由于我们建议使用唯一ID作为key，这种基于index的操作会变得不那么可靠。
      //    更好的方式是在条目对象里存ID，然后通过ID查找index进行操作。

      // 3. 执行删除
      settingEntries.value.splice(index, 1);

      ElMessage.success('设定条目已删除');
    })
    .catch(() => {
      // 用户取消删除
    });
};
const fetchKnowledgeBaseData = async (kbId: string) => {
  console.log('正在加载知识库 ID:', kbId)
  isLoading.value = true
  aiError.value = '' // Reset AI error on load

  // Simulate network delay
  await new Promise(resolve => setTimeout(resolve, 500))

  let mockData: any = {}
  if (kbId === 'kb-001') {
    mockData = {
      novelTitle: '艾琳的日记',
      characters: [
        {
          id: 1,
          name: '艾琳',
          alias: '天才少女',
          description: '本作主角，坚强、聪明，拥有特殊天赋。',
          backstory: '...'
        },
        {
          id: 2,
          name: '雷文',
          alias: '神秘的守护者',
          description: '沉默寡言，身份神秘，暗中保护艾琳。',
          backstory: '...'
        },
      ],
      settings: [
        {title: '魔法系统设定', content: '这个世界的魔法分为...'},
        {title: '地下室 (Day 0)', content: '艾琳醒来的地方，阴暗潮湿...'},
        {title: '雨夜发电厂', content: '关键剧情发生地...'}
      ],
      plot: '第一章：雨夜发电厂的逃亡...\n第二章：在钟中人那里获知真相...\n...\n第七章：发现自己是陨落的天才...',
      style: {
        tone: ['dark', 'suspenseful'],
        perspective: 'third_person_limited',
        reference: '文笔冷静，侧重于氛围描写。',
        customPrompt: 'AI 在续写时，请确保艾琳的对话符合她坚强独立的性格。'
      }
    }
  } else if (kbId === 'kb-002') {
    mockData = {
      novelTitle: '九州·缥缈录 (示例)',
      characters: [
        {
          id: 3,
          name: '吕归尘',
          alias: '阿苏勒',
          description: '北陆青阳部世子，体质孱弱，但内心强大。',
          backstory: '...'
        },
        {
          id: 4,
          name: '姬野',
          alias: '野尘',
          description: '出身东陆贵族，庶出，不被重视，梦想是“我要这天下的权柄”。',
          backstory: '...'
        },
      ],
      settings: [
        {title: '北陆', content: '冰天雪地的苦寒之地...'},
        {title: '东陆', content: '繁华的诸侯列国...'},
      ],
      plot: '少年们相遇，在乱世中成长与抉择...',
      style: {
        tone: ['epic', 'dark'],
        perspective: 'third_person_omniscient',
        reference: '江南风格，宏大叙事与细腻情感结合。',
        customPrompt: ''
      }
    }
  } else {
    // 模拟新创建的空知识库
    mockData = {
      novelTitle: '新知识库 (请在列表页修改)',
      characters: [],
      settings: [],
      plot: '',
      style: {tone: [], perspective: 'third_person_limited', reference: '', customPrompt: ''}
    }
  }

  novelTitle.value = mockData.novelTitle
  characterList.value = mockData.characters
  settingEntries.value = mockData.settings
  plotOutline.value = mockData.plot
  Object.assign(writingStyle, mockData.style)

  isLoading.value = false
  ElMessage.success(`知识库《${novelTitle.value}》加载完毕！`)
}

onMounted(() => {
  fetchKnowledgeBaseData(id)
})

const saveAll = async () => {
  console.log(`正在保存 ID 为 ${id} 的知识库...`)

  const dataToSave = {
    characters: characterList.value,
    settings: settingEntries.value,
    plot: plotOutline.value,
    style: writingStyle
  }

  console.log('保存的数据:', dataToSave)

  ElMessage.success('知识库已全部保存！')
  // (真实API) ...
  // fetchKnowledgeBaseData(id) // (可选) 保存后重新同步一次数据
}

// --- 角色管理方法 ---
const handleAddCharacter = () => {
  isEditing.value = false
  editingCharacter.value = {
    id: null,
    name: '',
    alias: '',
    description: '',
    backstory: '',
  }
  characterDrawerVisible.value = true
}

const handleEditCharacter = (char) => {
  isEditing.value = true
  editingCharacter.value = {...char}
  characterDrawerVisible.value = true
}

const handleSaveCharacter = () => {
  if (!editingCharacter.value.name) {
    ElMessage.error('角色姓名不能为空！')
    return
  }

  if (isEditing.value) {
    const index = characterList.value.findIndex(c => c.id === editingCharacter.value.id)
    if (index !== -1) {
      characterList.value[index] = {...editingCharacter.value}
    }
  } else {
    characterList.value.push({
      ...editingCharacter.value,
      id: Date.now(),
    })
  }
  characterDrawerVisible.value = false
  ElMessage.success(isEditing.value ? '角色已更新' : '角色已添加')
}

const handleDeleteCharacter = (id: number) => {
  ElMessageBox.confirm(
    '确定要删除这个角色吗？此操作不可撤销。',
    '警告',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    characterList.value = characterList.value.filter(c => c.id !== id)
    ElMessage.success('角色已删除')
  }).catch(() => {
  })
}

// --- 设定管理 ---
const handleAddSettingEntry = () => {
  const newEntry = {
    id: Date.now() + Math.random(), // 生成一个临时唯一ID，实际项目中最好用 UUID 库
    title: '新设定条目 (点击编辑标题)',
    content: ''
  };
  settingEntries.value.push(newEntry);
  activeSettingEntry.value = settingEntries.value.length - 1;
};

// --- AI Drawer Methods ---
const toggleAIDrawer = () => {
  aiDrawerVisible.value = !aiDrawerVisible.value;
};

const handleAIDrawerClose = (done: Function) => {
  // Optional: Add confirmation before closing if there's unsaved work or ongoing process
  // For now, just close it.
  done(); // Call done() to actually close the drawer
};

const handleGenerateWithAI = async () => {
  if (!aiInput.value.trim()) {
    aiError.value = '请输入提示词';
    return;
  }

  aiLoading.value = true;
  aiError.value = '';
  aiResponse.value = '';

  try {
    // Simulate an API call with a timeout
    await new Promise(resolve => setTimeout(resolve, 1500));

    // Simulate different responses based on input keywords (demo logic)
    let responseText = '';
    const lowerInput = aiInput.value.toLowerCase();
    if (lowerInput.includes('开头')) {
      responseText = `根据你提供的大纲和角色设定，这里是一个可能的开头场景建议：\n\n[场景：雨夜发电厂外]\n\n艾琳紧握着从雷文那里得到的旧怀表，冰冷的雨水顺着她的脸颊滑落。远处传来机器低沉的轰鸣声，夹杂着不明的金属摩擦声。她知道，必须在天亮前离开这里，否则...`;
    } else if (lowerInput.includes('角色')) {
      responseText = `关于角色发展，考虑到艾琳的坚强和雷文的神秘，可以设计一个情节让他们之间产生信任危机，比如艾琳发现雷文隐瞒了关于她过去的某个重要信息。`;
    } else {
      // Default generic response
      responseText = `收到你的请求：“${aiInput.value}”。\n\n我已经参考了知识库中的信息（包括大纲、角色和设定），这是我的分析和建议：\n\n1. 可以考虑将核心冲突与世界观中的“魔法系统设定”联系起来。\n2. 艾琳的性格特点很适合推动情节发展，尤其是在面对道德困境时。\n3. 结合“${Array.isArray(writingStyle.tone) ? writingStyle.tone.join(', ') : ''}”的基调，建议增加一些紧张感或悬念元素。\n\n希望对你有帮助！`;
    }

    aiResponse.value = responseText;

  } catch (err) {
    console.error("AI Generation Error:", err);
    aiError.value = 'AI 生成失败，请稍后重试。';
  } finally {
    aiLoading.value = false;
  }
};

</script>

<style scoped>
/* Ensure full height for main layout */
html, body, #app, .el-container {
  height: 100%;
}

/* 主容器 flex 布局 */
.h-full.flex { /* 对应 template 中最外层的 div */
  height: 100vh; /* 确保占满视口高度 */
}

/* Tabs 样式保持不变或微调 */
.el-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.el-tabs__content {
  flex: 1;
  overflow: auto;
}

/* Optional: Improve appearance of alerts */
.el-alert {
  line-height: 1.5;
}

/* AI Sidebar 内部滚动区域样式已在 template 中通过 class 处理 */
/* 确保 Flex 子项正确收缩 */
.min-w-0 {
  min-width: 0; /* 允许 flex item 收缩到内容宽度以下，防止溢出 */
}

.min-h-0 {
  min-height: 0; /* 同理，允许 flex item 收缩高度 */
}
</style>
