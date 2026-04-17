-- ============================================================
-- Manga Translate 最终数据库 Schema
-- 由所有迁移文件合并而来 (V1 ~ V2.6)
-- ============================================================

-- ==================== 表结构 ====================

-- 1. 用户表
CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. 翻译配置表
CREATE TABLE translate_configs
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users (id),
    name                 VARCHAR(100) NOT NULL,
    is_default           BOOLEAN               DEFAULT FALSE,
    target_lang          VARCHAR(10)  NOT NULL  DEFAULT 'CHS',
    translator           VARCHAR(30)            DEFAULT 'qwen3',
    detector             VARCHAR(20)            DEFAULT 'ctd',
    detection_size       INT                    DEFAULT 1536,
    text_threshold       DECIMAL(3, 2)          DEFAULT 0.5,
    box_threshold        DECIMAL(3, 2)          DEFAULT 0.8,
    unclip_ratio         DECIMAL(3, 1)          DEFAULT 2.5,
    ocr                  VARCHAR(20)            DEFAULT '48px',
    inpainter            VARCHAR(20)            DEFAULT 'lama_mpe',
    inpainting_size      INT                    DEFAULT 2560,
    inpainting_precision VARCHAR(10)            DEFAULT 'bf16',
    renderer             VARCHAR(20)            DEFAULT 'default',
    alignment            VARCHAR(10)            DEFAULT 'auto',
    direction            VARCHAR(15)            DEFAULT 'auto',
    font_size_offset     INT                    DEFAULT 0,
    mask_dilation_offset INT                    DEFAULT 20,
    kernel_size          INT                    DEFAULT 5,
    upscaler             VARCHAR(20)            DEFAULT 'esrgan',
    upscale_ratio       INT                    DEFAULT 2,
    colorizer            VARCHAR(10)            DEFAULT 'none',
    extra_config         JSONB,
    source_lang          VARCHAR(20)            DEFAULT 'japanese',
    use_mocr_merge       BOOLEAN               DEFAULT false,
    llm_config_id        BIGINT,
    created_at           TIMESTAMP    NOT NULL  DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL  DEFAULT NOW()
);

-- 3. 漫画表
CREATE TABLE mangas
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES users (id),
    title              VARCHAR(200) NOT NULL,
    author             VARCHAR(100),
    description        TEXT,
    cover_url          VARCHAR(500),
    page_count         INT          NOT NULL DEFAULT 0,
    tags               VARCHAR(500),
    reading_direction  VARCHAR(10)           DEFAULT 'rtl',
    last_read_page     INT                   DEFAULT 0,
    last_read_at       TIMESTAMP,
    active_config_id   BIGINT REFERENCES translate_configs (id),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 4. 章节表
CREATE TABLE chapters
(
    id             BIGSERIAL PRIMARY KEY,
    manga_id       BIGINT       NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    chapter_number INT          NOT NULL,
    page_count     INT                   DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (manga_id, chapter_number)
);

-- 5. 漫画页面表
CREATE TABLE manga_pages
(
    id                   BIGSERIAL PRIMARY KEY,
    manga_id             BIGINT       NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    chapter_id          BIGINT       NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    page_number          INT          NOT NULL,
    original_filename   VARCHAR(255),
    image_path          VARCHAR(500) NOT NULL,
    thumbnail_path     VARCHAR(500),
    width               INT,
    height              INT,
    file_size           BIGINT,
    is_translated       BOOLEAN               DEFAULT FALSE,
    translated_image_path VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (chapter_id, page_number)
);

-- 6. LLM 配置表
CREATE TABLE llm_configs
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT REFERENCES users (id),
    name          VARCHAR(100) NOT NULL,
    provider      VARCHAR(30)  NOT NULL,
    api_key       VARCHAR(500) NOT NULL,
    model_name    VARCHAR(100) NOT NULL,
    base_url      VARCHAR(500),
    multimodal    BOOLEAN               DEFAULT false,
    secret_key    VARCHAR(500),
    is_system     BOOLEAN               DEFAULT false,
    is_default    BOOLEAN               DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 7. 翻译记录表
CREATE TABLE translation_records
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users (id),
    manga_id              BIGINT      NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    chapter_id            BIGINT,
    page_id               BIGINT      NOT NULL REFERENCES manga_pages (id) ON DELETE CASCADE,
    page_number           INT,
    config_id             BIGINT REFERENCES translate_configs (id),
    status                VARCHAR(20) NOT NULL DEFAULT 'pending',
    translated_image_path VARCHAR(500),
    translation_json      JSONB,
    error_message         TEXT,
    duration_ms           INT,
    created_at            TIMESTAMP   NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMP
);

-- 8. 翻译任务表
CREATE TABLE translation_tasks
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    manga_id        BIGINT      NOT NULL REFERENCES mangas (id) ON DELETE CASCADE,
    config_id       BIGINT REFERENCES translate_configs (id),
    total_pages     INT         NOT NULL,
    completed_pages INT                  DEFAULT 0,
    failed_pages   INT                  DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ==================== 外键约束 (最后添加) ====================

ALTER TABLE translate_configs ADD CONSTRAINT fk_translate_configs_llm FOREIGN KEY (llm_config_id) REFERENCES llm_configs(id);

-- ==================== 索引 ====================

CREATE INDEX idx_translation_records_page_created ON translation_records (page_id, created_at DESC);
CREATE INDEX idx_translation_records_chapter ON translation_records (chapter_id);
CREATE INDEX idx_chapters_manga ON chapters (manga_id);
CREATE INDEX idx_manga_pages_chapter ON manga_pages (chapter_id);
CREATE INDEX idx_llm_configs_user ON llm_configs (user_id);

-- ==================== 系统内置数据 ====================

-- 系统内置用户 (id=0 表示系统预设)
INSERT INTO users (id, username, password_hash)
VALUES (0, '__system__', '__nologin__');

-- 系统内置翻译预设
INSERT INTO translate_configs (user_id, name, is_default, target_lang, translator, detector, detection_size, box_threshold, unclip_ratio, inpainting_size, mask_dilation_offset, kernel_size, upscaler, upscale_ratio)
VALUES
    (0, '日漫翻中文 (Qwen3)', TRUE, 'CHS', 'qwen3:4b', 'ctd', 1536, 0.8, 2.5, 2560, 20, 5, 'esrgan', 2),
    (0, '日漫翻英文 (Qwen3)', FALSE, 'ENG', 'qwen3:4b', 'ctd', 1536, 0.8, 2.5, 2560, 20, 5, 'esrgan', 2),
    (0, '韩漫翻中文 (Qwen3)', FALSE, 'CHS', 'qwen3:4b', 'ctd', 1536, 0.8, 2.5, 2560, 20, 5, 'esrgan', 2),
    (0, '通用翻中文 (Qwen3)', FALSE, 'CHS', 'qwen3:4b', 'ctd', 1536, 0.8, 2.5, 2560, 20, 5, 'esrgan', 2);

-- 系统内置 LLM 配置 (Ollama 预设)
INSERT INTO llm_configs (user_id, name, provider, api_key, model_name, multimodal, is_system, is_default, created_at, updated_at)
VALUES
    (NULL, 'Ollama Qwen3 4B', 'ollama', 'ollama', 'qwen3:4b', false, true, false, NOW(), NOW()),
    (NULL, 'Ollama Qwen3 8B', 'ollama', 'ollama', 'qwen3:8b', false, true, false, NOW(), NOW()),
    (NULL, 'Ollama Qwen3.5 9B (多模态)', 'ollama', 'ollama', 'qwen3.5:9b', true, true, false, NOW(), NOW());

-- 设置默认 LLM
UPDATE llm_configs SET is_default = true WHERE is_system = true AND model_name = 'qwen3:4b';

-- 重置序列起始值 (确保新插入数据 id 连续)
SELECT setval('users_id_seq', 1, false);
