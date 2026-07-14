CREATE TABLE character_cards (
                                 id BIGSERIAL PRIMARY KEY,
                                 book_id bigint NOT NULL,          -- 关联小说项目ID
                                 name VARCHAR(100) NOT NULL,               -- 角色姓名（唯一）
                                 description TEXT,                         -- 角色描述（简短）
                                 personality TEXT,                         -- 性格特点
                                 background TEXT,                          -- 背景故事
                                 appearance TEXT,                          -- 外貌描述
                                 age INTEGER,                              -- 年龄
                                 gender VARCHAR(20),                       -- 性别
                                 occupation VARCHAR(100),                  -- 职业
                                 abilities TEXT,                           -- 特殊能力
                                 weaknesses TEXT,                          -- 弱点
                                 goals TEXT,                               -- 目标/动机
                                 relationships JSONB,                      -- 与其他角色的关系（JSON格式）
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 约束
                                 CONSTRAINT uk_project_name UNIQUE (book_id, name),  -- 同项目内角色名唯一
                                 CONSTRAINT chk_age CHECK (age >= 0 AND age <= 3000)    -- 年龄合理性检查
);

-- 索引优化
CREATE INDEX idx_character_cards_project_id ON character_cards(book_id);
CREATE INDEX idx_character_cards_name ON character_cards(name);

CREATE TABLE character_relationships (
    id BIGSERIAL PRIMARY KEY,
    book_id bigint NOT NULL,
    from_character_id BIGINT NOT NULL,        -- 发起关系的角色ID
    to_character_id BIGINT NOT NULL,          -- 接受关系的角色ID
    relationship_type VARCHAR(50) NOT NULL,   -- 关系类型（如：friend, enemy, lover, family）
    description TEXT,                         -- 关系描述
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束（可选）
    -- CONSTRAINT fk_from_character FOREIGN KEY (from_character_id) REFERENCES character_cards(id),
    -- CONSTRAINT fk_to_character FOREIGN KEY (to_character_id) REFERENCES character_cards(id),

    -- 约束：不能自己和自己建立关系
    CONSTRAINT chk_different_characters CHECK (from_character_id != to_character_id)
);

CREATE INDEX idx_character_relationships_project ON character_relationships(book_id);
CREATE INDEX idx_character_relationships_from ON character_relationships(from_character_id);
CREATE INDEX idx_character_relationships_to ON character_relationships(to_character_id);
INSERT INTO character_cards (id, book_id, name, description, personality, background, appearance, age, gender,
                             occupation, abilities, weaknesses, goals, relationships)
VALUES (1978743650141601794, 12345, '林晓月', '一位拥有神秘力量的年轻女子，外表柔弱但内心坚强，背负着守护古老秘密的使命。',
        '冷静沉着，善于观察，对陌生人保持警惕但对自己信任的人极为忠诚。偶尔会因过度理性而显得冷漠。',
        '出生于一个古老的守护者家族，从小接受严格的训练。家族世代守护着一件能够影响世界平衡的神秘遗物。',
        '黑色长发及腰，眼眸呈深紫色，身高165cm，体型纤细但肌肉线条明显。常穿着深色修身服饰，佩戴着一枚家传的银色吊坠。', 22,
        '女', '古董店店主/秘密守护者', '能够感知和操纵能量流动，精通各种格斗技巧，拥有超凡的记忆力和分析能力。',
        '对家族使命过于执着，有时会因此忽略个人情感需求；紫色眼眸在情绪激动时会发光，容易暴露身份。',
        '保护神秘遗物不被邪恶势力获取，解开家族诅咒的真相，找到值得托付使命的继承人。', '[{"id":1, "bookId":12345,
        "fromCharacterId":1001, "toCharacterId":1002, "relationshipType":"师徒",
        "description":"林晓月是陈老师的得意门生，从小接受其教导", "createdAt":"Jan 15, 2023, 4:00:00 PM",
        "updatedAt":"Jun 20, 2023, 6:30:00 PM"}, {"id" : 2, "bookId":12345, "fromCharacterId":1001,
        "toCharacterId":1003, "relationshipType":"敌对", "description":"与神秘组织的追猎者有着不可调和的矛盾",
        "createdAt":"Feb 10, 2023, 10:20:00 PM", "updatedAt":"May 19, 2023, 12:45:00 AM"}, {"id" : 3, "bookId":12345,
        "fromCharacterId":1001, "toCharacterId":1004, "relationshipType":"盟友",
        "description":"与同为守护者后裔的李明远建立了信任关系", "createdAt":"Mar 5, 2023, 5:15:00 PM",
        "updatedAt":"Jul 12, 2023, 7:20:00 PM"}]')

CREATE TABLE conversation_message_blocks (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL,
    block_data_jsonb JSONB NOT NULL, -- 存储一个 List<Message> 的 JSONB
    message_count INTEGER NOT NULL,  -- 块中包含的消息数量
    start_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- 块中第一条消息的时间
    end_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,   -- 块中最后一条消息的时间
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_message_blocks_conversation_id ON conversation_message_blocks(conversation_id);
CREATE INDEX idx_message_blocks_created_at ON conversation_message_blocks(created_at);
