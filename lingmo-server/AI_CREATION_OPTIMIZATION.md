# AI小说创作功能优化方案

> 文档版本：v1.0
> 分析时间：2026-04-22
> 项目模块：novel-creation-platform-v2 / AI创作系统

---

## 一、现有功能分析

### 1.1 当前核心架构

```
┌─────────────────────────────────────────────────────────────┐
│                    NovelAICreationController                 │
│         /polish  /activate  /create  /continue  /dialogue   │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    NovelAICreationService                    │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │
│  │ 角色卡注入   │  │ RAG检索     │  │ 对话记忆(Redis)      │  │
│  └─────────────┘  └─────────────┘  └──────────────────────┘  │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │   ChatClient    │
                    │   (流式输出)     │
                    └─────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
              ┌───────────┐       ┌───────────┐
              │  DeepSeek  │       │  PgVector │
              │   LLM     │       │  (RAG)   │
              └───────────┘       └───────────┘
```

### 1.2 当前亮点

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 多创作模式 | ✅ 已实现 | 续写、润色、灵感、对话 |
| 流式响应 | ✅ 已实现 | SSE实时输出 |
| RAG增强 | ✅ 已实现 | PgVector向量检索 |
| 角色卡上下文 | ✅ 已实现 | 自动注入角色信息 |
| 对话记忆 | ✅ 已实现 | Redis存储会话历史 |
| 多数据源 | ✅ 已实现 | 小说系统+知识库分离 |

### 1.3 当前不足

| 问题 | 严重程度 | 说明 |
|------|---------|------|
| 缺乏章节上下文 | 🟠 中 | 未传递章节内容给AI |
| 缺乏前文摘要 | 🟠 中 | 上下文窗口有限时无摘要 |
| 缺乏内容质量控制 | 🟠 中 | 无生成质量评估机制 |
| 缺乏创作版本管理 | 🟡 低 | 无AI创作历史追踪 |
| 角色卡功能单薄 | 🟡 低 | 仅存储，未充分利用 |
| 缺乏创作引导 | 🟡 低 | 无写作风格指导 |

---

## 二、优化方案（六大模块）

### 模块一：增强上下文感知 ⭐⭐⭐⭐⭐

#### 2.1.1 传递章节内容作为上下文

**问题**：
当前 `AiChatRequest` 只包含 `userInput`，未传递当前章节内容，导致AI无法基于前文风格续写。

**优化方案**：

在 `AiChatRequest` 中新增字段：
```java
@Data
public class AiChatRequest implements Serializable {
    // ... 现有字段

    // ⭐ 新增：当前章节内容（前500字）
    private String currentChapterContent;

    // ⭐ 新增：前几章的关键情节摘要
    private List<String> previousChapterSummary;

    // ⭐ 新增：当前章节的已知剧情（前情提要）
    private String plotSummary;
}
```

**修改 `NovelAICreationService.createContent()`**：
```java
public Flux<String> createContent(AiChatRequest aiChatRequest) {
    // ... 现有逻辑

    // 构建增强上下文
    String enhancedContext = buildEnhancedContext(aiChatRequest);

    return chatClient
            .prompt()
            .system(systemPrompt)
            .user(enhancedContext)  // 使用增强上下文
            // ...
}
```

#### 2.1.2 智能章节摘要生成

**问题**：
长篇小说上下文窗口有限，需要智能摘要前文关键信息。

**优化方案**：

新增 `ChapterSummaryService`：
```java
@Service
public class ChapterSummaryService {

    @Autowired
    private ChatClient chatClient;

    /**
     * 生成章节摘要（用于AI创作上下文）
     * @param chapterId 章节ID
     * @param bookId 书籍ID
     * @return 章节摘要
     */
    public String generateChapterSummary(Long chapterId, Long bookId) {
        // 1. 获取前3章内容
        List<String> recentContents = getRecentChapterContents(bookId, 3);

        // 2. 调用AI生成摘要
        String prompt = String.format("""
            请为以下小说章节生成一段简短摘要（100字以内），包含：
            - 主要人物
            - 关键事件
            - 当前状态

            章节内容：
            %s
            """, String.join("\n---\n", recentContents));

        return chatClient.prompt()
                .system("你是一个小说情节分析师，负责提取关键信息。")
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 获取最近N章的内容
     */
    private List<String> getRecentChapterContents(Long bookId, int count) {
        // 实现从数据库获取最近章节内容
        // ...
    }
}
```

#### 2.1.3 世界观知识图谱关联

**优化方案**：

增强 RAG 检索，不仅检索文档，还关联世界观：
```java
// 在 NovelAICreationService 中增强检索逻辑
private String buildWorldKnowledgeContext(Long bookId, String currentPlot) {
    // 1. 检索相关知识库文档
    List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                    .query(currentPlot)
                    .topK(5)
                    .filterExpression("bookId == " + bookId)
                    .build()
    );

    // 2. 构建知识图谱查询
    // 获取与当前情节相关的角色和事件

    // 3. 整合为上下文
    return formatWorldKnowledgeContext(relevantDocs, relatedCharacters);
}
```

---

### 模块二：角色卡深度应用 ⭐⭐⭐⭐⭐

#### 2.2.1 角色关系可视化数据

**当前实现**：
`CharacterCards` 实体存储了 `relationships` (JSONB)，但未充分利用。

**优化方案**：

```java
// 新增 CharacterRelationshipService
@Service
public class CharacterRelationshipService {

    /**
     * 构建角色关系网络图数据
     * @param bookId 书籍ID
     * @return 关系图数据（用于前端可视化）
     */
    public RelationshipGraph buildRelationshipGraph(Long bookId) {
        List<CharacterCardsVo> characters = characterCardsService.getCharacterCardsByProjectId(bookId);

        // 构建节点和边
        List<GraphNode> nodes = characters.stream()
                .map(c -> new GraphNode(c.getId(), c.getName(), c.getPersonality()))
                .toList();

        List<GraphEdge> edges = characters.stream()
                .filter(c -> c.getRelationships() != null)
                .flatMap(c -> c.getRelationships().stream()
                        .map(r -> new GraphEdge(c.getId(), r.getTargetId(), r.getType())))
                .toList();

        return new RelationshipGraph(nodes, edges);
    }
}
```

#### 2.2.2 角色冲突检测

**亮点功能**：
AI在创作时检测角色行为是否符合设定，自动提醒冲突。

```java
/**
 * 检测角色行为与设定冲突
 */
public List<CharacterConflict> detectConflicts(Long bookId, String newContent) {
    List<CharacterCardsVo> characters = characterCardsService.getCharacterCardsByProjectId(bookId);

    // 构建冲突检测Prompt
    String prompt = String.format("""
        请分析以下小说内容，检测角色行为是否与设定冲突：

        【角色设定】
        %s

        【待检测内容】
        %s

        检测维度：
        1. 性格冲突（行为与性格不符）
        2. 能力冲突（使用未设定的能力）
        3. 关系冲突（态度转变不合理）
        4. 世界观冲突（违反设定规则）

        输出JSON格式的冲突列表。
        """, formatCharacterSettings(characters), newContent);

    String result = chatClient.prompt()
            .system("你是角色行为分析师，专注于检测创作中的角色设定冲突。")
            .user(prompt)
            .call()
            .content();

    return parseConflicts(result);
}
```

#### 2.2.3 角色弧线追踪

**亮点功能**：
追踪角色在故事中的成长弧线，帮助作者理解角色发展。

```java
/**
 * 生成角色弧线报告
 */
public CharacterArcReport generateCharacterArc(Long bookId, Long characterId) {
    // 1. 获取该角色在所有章节中的出场内容
    List<ChapterContent> appearances = getCharacterAppearances(bookId, characterId);

    // 2. 分析角色变化
    String analysis = chatClient.prompt()
            .system("你是角色发展分析师，分析角色在故事中的成长轨迹。")
            .user("分析以下内容中角色的心理变化、目标达成、人际关系变化：\n" +
                    appearances.stream()
                            .map(c -> "章节" + c.getChapterNum() + ": " + c.getContent())
                            .collect(Collectors.joining("\n")))
            .call()
            .content();

    // 3. 生成弧线报告
    return parseCharacterArc(analysis);
}
```

---

### 模块三：创作质量控制 ⭐⭐⭐⭐

#### 2.3.1 生成内容质量评分

**亮点功能**：
AI生成内容后自动评分，提供改进建议。

```java
// 新增 ContentQualityService
@Service
public class ContentQualityService {

    /**
     * 评估创作内容质量
     */
    public QualityReport evaluateQuality(String content, CreationContext context) {
        String prompt = String.format("""
            请从以下维度评估小说内容的质量，并为每个维度打分（1-10分）：

            评估维度：
            1. 文笔流畅度 - 语言表达是否流畅自然
            2. 情节合理性 - 事件发展是否符合逻辑
            3. 人物塑造 - 角色行为是否符合设定
            4. 情感共鸣 - 能否引发读者情感共鸣
            5. 世界观一致性 - 是否符合设定的世界观
            6. 可读性 - 段落结构和节奏是否合适

            【待评估内容】
            %s

            【创作类型】%s

            输出JSON格式：
            {
              "scores": {
                "writingFluency": 8,
                "plotLogic": 7,
                "characterization": 9,
                "emotionalResonance": 8,
                "worldConsistency": 9,
                "readability": 7
              },
              "totalScore": 8.0,
              "suggestions": ["建议1", "建议2"],
              "highlights": ["亮点1", "亮点2"]
            }
            """, content, context.getCreationType());

        String result = chatClient.prompt()
                .system("你是一位专业的小说编辑，专注于内容质量评估。")
                .user(prompt)
                .call()
                .content();

        return parseQualityReport(result);
    }
}
```

#### 2.3.2 敏感内容检测

**亮点功能**：
自动检测生成内容中的敏感信息。

```java
@Service
public class ContentFilterService {

    private static final List<String> SENSITIVE_KEYWORDS = Arrays.asList(
            "自杀", "自残", "暴力", "色情"
    );

    /**
     * 检测敏感内容
     * @return 检测结果
     */
    public FilterResult checkContent(String content) {
        // 1. 关键词快速检测
        List<String> foundKeywords = detectKeywords(content);

        // 2. AI深度检测
        String aiCheckResult = chatClient.prompt()
                .system("你是一个内容安全检测系统，检测以下内容是否包含敏感信息。")
                .user("检测以下内容：\n" + content)
                .call()
                .content();

        // 3. 综合判断
        return combineResults(foundKeywords, aiCheckResult);
    }
}
```

#### 2.3.3 写作风格指导

**亮点功能**：
提供可配置的写作风格指导。

```java
// 在 AiChatRequest 中新增风格配置
@Data
public class AiChatRequest {
    // ... 现有字段

    // ⭐ 新增：写作风格配置
    private WritingStyleConfig styleConfig;
}

@Data
public class WritingStyleConfig {
    // 叙事视角 (第一人称/第三人称/全知视角)
    private NarrativePerspective perspective = NarrativePerspective.THIRD_PERSON;

    // 叙事节奏 (快节奏/中等/慢节奏)
    private NarrativePace pace = NarrativePace.MEDIUM;

    // 情感基调 (轻松/严肃/悬疑/浪漫)
    private EmotionalTone tone = EmotionalTone.NEUTRAL;

    // 对话风格 (口语化/文雅/地方特色)
    private DialogueStyle dialogueStyle = DialogueStyle.NATURAL;

    // 描写密度 (简洁/适中/详细)
    private DescriptionDensity descriptionDensity = DescriptionDensity.MEDIUM;
}
```

---

### 模块四：创作版本管理 ⭐⭐⭐⭐

#### 2.4.1 AI创作版本追踪

**亮点功能**：
保存每次AI创作的版本，支持回滚和对比。

```java
// 新增 AI创作版本表和服务
@Entity
@Table(name = "ai_creation_version")
public class AiCreationVersion {
    Long id;
    Long chapterId;
    Long bookId;
    String version;           // 版本号
    String content;          // AI生成内容
    String prompt;           // 使用的Prompt
    Integer qualityScore;    // 质量评分
    String creationType;     // 创作类型
    Date createdAt;
    Boolean isAdopted;        // 是否被采纳
}

// Service
@Service
public class AiCreationVersionService {

    @Autowired
    private AiCreationVersionMapper versionMapper;

    /**
     * 保存AI创作版本
     */
    public Long saveVersion(AiCreationRequest request, String content, Integer qualityScore) {
        AiCreationVersion version = new AiCreationVersion();
        version.setChapterId(request.getChapterId());
        version.setBookId(request.getBookId());
        version.setContent(content);
        version.setPrompt(buildPrompt(request));
        version.setQualityScore(qualityScore);
        version.setCreationType(request.getCreationType());
        version.setCreatedAt(new Date());
        version.setIsAdopted(false);
        return versionMapper.insert(version);
    }

    /**
     * 获取章节的创作历史
     */
    public List<AiCreationVersion> getVersionHistory(Long chapterId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<AiCreationVersion>()
                        .eq(AiCreationVersion::getChapterId, chapterId)
                        .orderByDesc(AiCreationVersion::getCreatedAt)
        );
    }

    /**
     * 对比两个版本的差异
     */
    public VersionDiff compareVersions(Long versionId1, Long versionId2) {
        AiCreationVersion v1 = versionMapper.selectById(versionId1);
        AiCreationVersion v2 = versionMapper.selectById(versionId2);
        return computeDiff(v1.getContent(), v2.getContent());
    }
}
```

#### 2.4.2 多版本分支创作

**亮点功能**：
允许同时探索多个创作方向。

```java
/**
 * 分支创作 - 同时生成多个版本供选择
 */
public Flux<List<CreationBranch>> createBranches(AiChatRequest request, int branchCount) {
    // 同时生成多个不同风格的版本
    return Flux.range(0, branchCount)
            .flatMap(index -> {
                request.setStyleVariation(index);  // 设置风格变体
                return aiCreationService.createContent(request)
                        .collectList()
                        .map(content -> new CreationBranch(index, String.join("", content)));
            })
            .collectList();
}
```

---

### 模块五：知识库增强 ⭐⭐⭐

#### 2.5.1 智能参考文献关联

**亮点功能**：
创作时自动关联知识库中的相关参考资料。

```java
@Service
public class ReferenceService {

    /**
     * 获取当前创作的相关参考资料
     */
    public List<Reference> getRelatedReferences(Long bookId, String currentContent) {
        // 1. 提取当前内容的关键概念
        List<String> keyConcepts = extractKeyConcepts(currentContent);

        // 2. 向量检索相关文档
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(String.join(", ", keyConcepts))
                        .topK(3)
                        .filterExpression("bookId == " + bookId)
                        .build()
        );

        // 3. 格式化参考资料
        return relevantDocs.stream()
                .map(doc -> new Reference(
                        doc.getId(),
                        doc.getMetadata("filename"),
                        doc.getMetadata("source"),
                        truncate(doc.getContent(), 200)  // 截取摘要
                ))
                .toList();
    }

    /**
     * 提取关键概念
     */
    private List<String> extractKeyConcepts(String content) {
        // 使用AI提取关键概念，或使用关键词提取算法
        // ...
    }
}
```

#### 2.5.2 创作素材库管理

```java
// 新增素材库功能
@Service
public class MaterialLibraryService {

    /**
     * 收藏创作素材
     */
    public void collectMaterial(Long bookId, String content, MaterialType type) {
        // 1. 创建素材记录
        // 2. 生成向量嵌入
        // 3. 存储到向量数据库
        // 4. 返回素材ID
    }

    /**
     * 搜索素材
     */
    public List<Material> searchMaterials(Long bookId, String query) {
        // 基于向量相似度搜索
    }
}

public enum MaterialType {
    SCENE,       // 场景描写
    DIALOGUE,    // 对话素材
    CHARACTER,   // 人物描写
    PLOT,        // 情节素材
    DESCRIPTION  // 环境描写
}
```

---

### 模块六：性能与体验优化 ⭐⭐⭐

#### 2.6.1 上下文预加载

**亮点功能**：
在用户输入时预先加载相关上下文，减少等待时间。

```java
@Service
public class ContextPreloader {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 预加载创作上下文
     */
    @Async
    public void preloadContext(Long bookId, Long chapterId) {
        String cacheKey = "preload:context:" + bookId + ":" + chapterId;

        // 检查是否已预加载
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            return;
        }

        // 异步加载
        ContextData context = ContextData.builder()
                .characterCards(characterCardsService.getCharacterCardsByProjectId(bookId))
                .recentChapters(getRecentChapterSummaries(bookId, 3))
                .worldKnowledge(getWorldKnowledge(bookId))
                .build();

        redisTemplate.opsForValue().set(cacheKey, context, Duration.ofHours(1));
    }
}
```

#### 2.6.2 智能Prompt缓存

**亮点功能**：
缓存常用的系统Prompt，避免重复构建。

```java
@Service
public class PromptCacheService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 获取或构建系统Prompt
     */
    public String getSystemPrompt(CreationContext context) {
        String cacheKey = "prompt:system:" + context.getBookId() + ":" + context.getCreationType();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String prompt = buildSystemPrompt(context);
        redisTemplate.opsForValue().set(cacheKey, prompt, Duration.ofHours(24));
        return prompt;
    }
}
```

#### 2.6.3 创作进度保存

**亮点功能**：
流式创作中途可保存进度，避免内容丢失。

```java
/**
 * 保存创作进度（中间状态）
 */
@PostMapping("/save-progress")
public BaseResponse<String> saveProgress(@RequestBody SaveProgressRequest request) {
    // 1. 保存当前进度到Redis（带过期时间）
    String progressKey = "creation:progress:" + request.getUserId() + ":" + request.getChapterId();
    redisTemplate.opsForValue().set(progressKey, request.getContent(), Duration.ofHours(24));

    // 2. 返回恢复码
    String recoveryCode = generateRecoveryCode();
    return ResultUtils.success(recoveryCode);
}

/**
 * 恢复创作进度
 */
@GetMapping("/restore-progress/{recoveryCode}")
public Flux<String> restoreProgress(@PathVariable String recoveryCode) {
    // 根据恢复码获取进度
    String content = getContentByRecoveryCode(recoveryCode);
    // 可以选择继续创作或重新生成
}
```

---

## 三、实现优先级建议

### 第一阶段（核心增强）- 预计开发时间：1周

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 章节上下文传递 | P0 | 传递当前章节内容给AI |
| 内容质量评分 | P0 | 评估AI生成内容 |
| 创作版本保存 | P1 | 保存AI创作历史 |

### 第二阶段（特色功能）- 预计开发时间：1周

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 角色冲突检测 | P1 | 检测角色行为与设定冲突 |
| 智能摘要生成 | P1 | 生成章节摘要作为上下文 |
| 写作风格配置 | P2 | 可配置的写作风格指导 |

### 第三阶段（体验优化）- 预计开发时间：1周

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 角色关系图谱 | P2 | 可视化角色关系 |
| 上下文预加载 | P2 | 减少等待时间 |
| 素材库管理 | P2 | 创作素材收藏 |

---

## 四、预期亮点效果

### 4.1 竞争优势

| 亮点 | 说明 |
|------|------|
| 深度上下文理解 | AI能理解前文情节，保持故事连贯性 |
| 角色一致性保障 | 自动检测角色行为冲突，保证角色塑造 |
| 专业质量评估 | AI生成内容自动评分，提供改进建议 |
| 创作版本管理 | 支持版本回溯、多版本对比 |
| 个性化风格 | 支持多种写作风格配置 |

### 4.2 差异化功能

```
┌─────────────────────────────────────────────────────────────┐
│                    小说创作平台亮点矩阵                      │
├─────────────────────────────────────────────────────────────┤
│  【智能】        │  【专业】        │  【协作】              │
│  ├─ 上下文感知  │  ├─ 质量评分     │  ├─ 版本管理          │
│  ├─ 预加载加速  │  ├─ 风格配置     │  ├─ 分支创作          │
│  └─ 智能摘要    │  └─ 敏感检测     │  └─ 素材库            │
├─────────────────────────────────────────────────────────────┤
│  【角色】        │  【世界观】      │  【数据驱动】          │
│  ├─ 角色卡深度  │  ├─ RAG检索      │  ├─ 创作分析          │
│  ├─ 冲突检测    │  ├─ 知识图谱     │  ├─ 趋势洞察          │
│  └─ 弧线追踪    │  └─ 参考关联     │  └─ 质量追踪          │
└─────────────────────────────────────────────────────────────┘
```

---

## 五、技术实现路径

### 5.1 新增Service类

```
service/
├── impl/
│   ├── NovelAICreationService.java      # 增强现有服务
│   ├── ContentQualityService.java        # 新增：内容质量评估
│   ├── ChapterSummaryService.java        # 新增：章节摘要生成
│   ├── CharacterRelationshipService.java # 新增：角色关系管理
│   ├── CharacterConflictService.java     # 新增：角色冲突检测
│   ├── AiCreationVersionService.java     # 新增：创作版本管理
│   ├── ReferenceService.java             # 新增：参考文献服务
│   └── ContextPreloader.java             # 新增：上下文预加载
```

### 5.2 新增Entity类

```
pojo/entity/
├── AiCreationVersion.java       # AI创作版本记录
├── MaterialLibrary.java         # 创作素材库
├── ChapterSummary.java          # 章节摘要
└── CharacterConflict.java       # 角色冲突记录
```

### 5.3 新增DTO类

```
pojo/dto/
├── ContentEvaluationRequest.java    # 内容评估请求
├── ContentEvaluationResponse.java   # 评估结果响应
├── WritingStyleConfig.java          # 写作风格配置
├── CreationBranchRequest.java       # 分支创作请求
└── SaveProgressRequest.java         # 保存进度请求
```

---

## 六、测试验证建议

### 6.1 功能测试用例

| 测试场景 | 预期结果 |
|---------|---------|
| 传递章节上下文创作 | AI生成内容与前文风格一致 |
| 质量评分功能 | 返回1-10分评分和改进建议 |
| 角色冲突检测 | 正确识别角色行为冲突 |
| 版本保存与回溯 | 可以查看历史版本并对比 |
| 分支创作 | 同时生成多个风格版本 |

### 6.2 性能测试

| 指标 | 目标 |
|------|------|
| 流式响应延迟 | < 500ms 首字响应 |
| 上下文预加载 | 后台完成，不阻塞主流程 |
| 向量检索 | < 100ms |

---

*文档结束*
*优化方案版本：v1.0*
*建议优先实现：第一阶段功能（核心增强）*