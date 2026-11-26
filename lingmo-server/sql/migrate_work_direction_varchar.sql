-- ============================================================================
-- 累积迁移：作品方向 work_direction 由 SMALLINT(0-男频/1-女频) 升级为 VARCHAR(20) 自由文本
-- 目标库：novel_system
-- 一次性脚本（ALTER COLUMN TYPE 需显式 USING 转换），可安全重放；已升级列会因类型不匹配被跳过并给出提示
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) author_info.work_direction
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'author_info' AND column_name = 'work_direction'
                 AND data_type = 'smallint') THEN
        ALTER TABLE author_info
            ALTER COLUMN work_direction TYPE VARCHAR(20)
            USING (CASE work_direction WHEN 0 THEN '男频' WHEN 1 THEN '女频' ELSE NULL END);
        ALTER TABLE author_info ALTER COLUMN work_direction SET DEFAULT '男频';
    ELSE
        RAISE NOTICE 'author_info.work_direction 已为字符类型或不存在，跳过';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2) book_category.work_direction（含唯一约束重建）
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'book_category' AND column_name = 'work_direction'
                 AND data_type = 'smallint') THEN
        ALTER TABLE book_category DROP CONSTRAINT IF EXISTS uk_category_direction_name;
        ALTER TABLE book_category
            ALTER COLUMN work_direction TYPE VARCHAR(20)
            USING (CASE work_direction WHEN 0 THEN '男频' WHEN 1 THEN '女频' ELSE NULL END);
        ALTER TABLE book_category ALTER COLUMN work_direction SET DEFAULT '男频';
        ALTER TABLE book_category
            ADD CONSTRAINT uk_category_direction_name UNIQUE (work_direction, name);
    ELSE
        RAISE NOTICE 'book_category.work_direction 已为 VARCHAR(20) 或不存在，跳过';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3) book_info.work_direction
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'book_info' AND column_name = 'work_direction'
                 AND data_type = 'smallint') THEN
        ALTER TABLE book_info
            ALTER COLUMN work_direction TYPE VARCHAR(20)
            USING (CASE work_direction WHEN 0 THEN '男频' WHEN 1 THEN '女频' ELSE NULL END);
        ALTER TABLE book_info ALTER COLUMN work_direction SET DEFAULT '男频';
    ELSE
        RAISE NOTICE 'book_info.work_direction 已为 VARCHAR 或不存在，跳过';
    END IF;
END $$;