-- ============================================================
-- 更新文章AI翻译提示词，支持TOON格式优化
-- 将翻译提示词改为TOON格式，减少50% token消耗
-- ============================================================

-- 更新文章AI助手配置的翻译提示词
UPDATE `sys_ai_config`
SET `llm_config` = JSON_SET(
    COALESCE(`llm_config`, JSON_OBJECT()),
    '$.prompt', 
    '将以下TOON格式数据从{source_lang}翻译为{target_lang}。\n\n规则：\n1. 保持TOON格式结构不变（2个空格缩进）\n2. 翻译title和content的值\n3. 保持Markdown格式\n4. 只返回TOON格式数据，不添加任何解释\n\n输入TOON数据：\n{toon_data}\n\n请返回翻译后的TOON数据，格式如下：\narticle:\n  title: (翻译后的{target_lang}标题)\n  content: (翻译后的{target_lang}内容)'
)
WHERE `config_type` = 'article_ai' 
  AND `config_name` = 'default';


-- ============================================================
-- 更新文章AI摘要生成提示词，支持TOON格式优化
-- 将摘要生成提示词改为TOON格式，预计减少15-25% token消耗
-- ============================================================

-- 更新文章AI助手配置的摘要生成提示词
UPDATE `sys_ai_config`
SET `summary_config` = JSON_SET(
    COALESCE(`summary_config`, JSON_OBJECT()),
    '$.prompt', 
    '请为以下{source_lang}文章生成多语言摘要，要求：\n1. 生成语言：{languages}\n2. 风格：{style_desc}\n3. 每个语言的摘要长度控制在{max_length}字符以内\n4. 保持TOON格式结构不变（2个空格缩进）\n5. 只返回TOON格式数据，不添加任何解释或markdown代码块标记\n6. 注意：为每个目标语言生成该语言的摘要（如需要英文摘要，则生成英文；如需要日文摘要，则生成日文）\n\n文章内容：\n\n{source_content}\n\n请返回TOON格式的摘要，格式如下：\n{toon_example}'
)
WHERE `config_type` = 'article_ai' 
  AND `config_name` = 'default';

