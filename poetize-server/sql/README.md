# SQL 脚本说明文档

## 目录结构

本目录包含项目的数据库脚本文件，分为以下两类：

### 1. 主数据库脚本

- **poetry.sql** - 当前完整数据库结构
- **poetry_old.sql** - 历史数据库结构备份（使用InnoDB引擎，当使用mysql作为数据库时使用）

### 2. 数据库迁移脚本

按时间顺序排列的增量迁移脚本，用于数据库结构的版本管理和升级。

## 迁移脚本命名规范

### 命名格式

```
YYYYMMDDHHMM.sql
```

- **YYYY**: 四位年份
- **MM**: 两位月份（01-12）
- **DD**: 两位日期（01-31）
- **HH**: 两位小时（00-23）
- **MM**: 两位分钟（00-59）

### 命名示例

- `202507220125.sql` - 2025年7月22日 01:25
- `202510191235.sql` - 2025年10月19日 12:35

### 命名规则说明

1. 文件名基于该脚本首次提交到 Git 仓库的时间
2. 按时间顺序自然排序，便于管理和执行
3. 时间戳精确到分钟，避免文件名冲突
4. 如果同一分钟内有多个脚本，手动调整分钟数（如 +1 分钟）

## 数据库引擎规范

### InnoDB 引擎（推荐）

所有迁移脚本中创建的表均使用 **InnoDB** 存储引擎：

```sql
CREATE TABLE `table_name` (
  ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表说明';
```

**InnoDB 优势：**

- 支持事务处理（ACID）
- 支持外键约束
- 支持行级锁，并发性能好
- 支持崩溃恢复

### Aria 引擎

仅 `poetry.sql` 使用 **Aria** 引擎（或其他引擎）以保证与旧版本的兼容性。

## 迁移脚本编写规范

### 1. 文件头部注释

每个迁移脚本应包含清晰的注释说明：

```sql
-- ============================================================
-- 功能说明：添加 xxx 功能
-- 变更内容：
--   1. 创建 xxx 表
--   2. 修改 xxx 字段
--   3. 添加 xxx 索引
-- 日期：YYYY-MM-DD
-- ============================================================
```

### 2. 表结构变更

#### 创建表

```sql
CREATE TABLE IF NOT EXISTS `table_name` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `field_name` varchar(100) NOT NULL COMMENT '字段说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_field_name` (`field_name`) COMMENT '字段索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表说明';
```

#### 添加字段

```sql
ALTER TABLE `table_name`
ADD COLUMN `new_field` VARCHAR(100) NULL COMMENT '字段说明' AFTER `existing_field`;
```

#### 修改字段

```sql
ALTER TABLE `table_name`
MODIFY COLUMN `field_name` VARCHAR(200) NOT NULL COMMENT '修改后的说明';
```

#### 删除字段

```sql
ALTER TABLE `table_name`
DROP COLUMN `field_name`;
```

### 3. 索引管理

#### 添加索引

```sql
-- 普通索引
CREATE INDEX idx_field_name ON table_name(field_name);

-- 唯一索引
CREATE UNIQUE INDEX uk_field_name ON table_name(field_name);

-- 联合索引
CREATE INDEX idx_field1_field2 ON table_name(field1, field2);
```

#### 删除索引

```sql
DROP INDEX idx_field_name ON table_name;
```

### 4. 数据迁移

#### 安全插入数据（防止重复）

**问题**：`ON DUPLICATE KEY UPDATE` 仅检查主键和唯一索引，如果业务键（如 `config_key`）没有唯一索引，会导致重复插入。

**推荐方式：使用 INSERT ... SELECT ... WHERE NOT EXISTS**

```sql
-- 先清理可能存在的重复数据
-- 保留ID最小的（最早的记录），删除ID大的（后来重复插入的）
DELETE t1 FROM `table_name` t1
INNER JOIN `table_name` t2
WHERE t1.business_key = t2.business_key
  AND t1.id > t2.id;

-- 安全插入：只在业务键不存在时插入
INSERT INTO `table_name` (field1, field2)
SELECT 'value1', 'value2'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `table_name` 
    WHERE business_key = 'value1'
);

-- 更新已存在的记录
UPDATE `table_name`
SET field2 = 'value2'
WHERE business_key = 'value1';
```

**不推荐方式（可能导致重复）**

```sql
-- 错误：如果没有唯一索引，每次执行都会插入新记录
INSERT INTO `sys_config` (config_key, config_value)
VALUES ('key1', 'value1')
ON DUPLICATE KEY UPDATE config_value = 'value1';
```

#### 数据更新

```sql
-- 条件更新
UPDATE `table_name`
SET field1 = 'new_value'
WHERE condition;
```

### 5. 安全性原则

- **表结构**：使用 `IF NOT EXISTS` 避免重复创建
- **删除操作**：使用 `IF EXISTS` 避免删除不存在的对象
- **数据插入**：使用 `INSERT ... SELECT ... WHERE NOT EXISTS` 防止重复（见第4节）
- **业务约束**：为业务主键添加唯一索引，从数据库层面防止重复
- **谨慎操作**：`DROP` 和 `DELETE` 操作前先确认影响范围
- **备份策略**：重要数据变更前必须备份

## 迁移脚本执行顺序

### 新环境部署

1. 执行 `poetry.sql` - 创建完整数据库结构
2. 按时间顺序执行所有迁移脚本（如有需要）

### 现有环境升级

1. 确认当前数据库版本（已执行到哪个迁移脚本）
2. 按时间顺序执行后续的迁移脚本
3. 建议在执行前备份数据库

### 执行示例

```bash
# 方式1：单个执行
mysql -u username -p database_name < 202507220125.sql

# 方式2：批量执行（按顺序）
for file in 2025*.sql; do
  echo "Executing $file..."
  mysql -u username -p database_name < "$file"
done
```

## 当前迁移脚本列表

| 文件名           | 创建时间         | 说明                                 |
| ---------------- | ---------------- | ------------------------------------ |
| 202507220125.sql | 2025-07-22 01:25 | 添加位置字段                         |
| 202507251802.sql | 2025-07-25 18:02 | 添加 QQ OAuth 配置                   |
| 202509260033.sql | 2025-09-26 00:33 | SEO 配置表结构                       |
| 202509292211.sql | 2025-09-29 22:11 | 添加百度 OAuth 配置                  |
| 202509292212.sql | 2025-09-29 22:12 | 添加动态标题设置                     |
| 202509292332.sql | 2025-09-29 23:32 | 更新导航配置                         |
| 202509302306.sql | 2025-09-30 23:06 | 添加移动端抽屉配置                   |
| 202510070226.sql | 2025-10-07 02:26 | 迁移站点地址配置                     |
| 202510092213.sql | 2025-10-09 22:13 | 添加 IM 聊天已读记录                 |
| 202510171712.sql | 2025-10-17 17:12 | 添加验证码配置表                     |
| 202510171717.sql | 2025-10-17 17:17 | 添加邮件配置表                       |
| 202510191235.sql | 2025-10-19 12:35 | AI 配置统一管理表 + 文章翻译摘要字段 |
| 202510251611.sql | 2025-10-25 16:11 | 添加联系方式管理                     |
| 202510261604.sql | 2025-10-26 16:04 | 文章点赞功能优化                     |
| 202510301340.sql | 2025-10-30 13:40 | 添加全局评论开关配置                 |
| 202511010001.sql | 2025-11-01 00:01 | 优化网页标题设置                     |
| 202511030001.sql | 2025-11-03 00:01 | SEO配置优化                          |
| 202511051500.sql | 2025-11-05 15:00 | 修复重复配置键 + 添加唯一索引        |

## 版本控制规范

### Git 提交规范

- 新增迁移脚本使用 `git add` 添加到版本控制
- 提交信息格式：`feat: 添加数据库迁移脚本 - [功能说明]`
- 示例：`feat: 添加数据库迁移脚本 - AI配置统一管理`

### 文件重命名

- 使用 PowerShell 的 `Rename-Item` 进行重命名
- 重命名后使用 `git add -A` 让 Git 识别重命名操作

## 注意事项

1. **不要修改已执行的迁移脚本**

   - 已在生产环境执行的脚本不应再修改
   - 如需调整，应创建新的迁移脚本进行修正
2. **字符集和排序规则**

   - 统一使用 `utf8mb4` 字符集
   - 推荐使用 `utf8mb4_general_ci` 或 `utf8mb4_unicode_ci` 排序规则
3. **字段注释**

   - 所有字段必须添加 `COMMENT` 注释
   - 注释应清晰说明字段用途和取值范围
4. **测试验证**

   - 在开发环境充分测试后再应用到生产环境
   - 验证数据完整性和索引有效性
5. **备份策略**

   - 生产环境执行迁移前必须备份数据库
   - 保留至少一周的数据库备份

## 常见问题

### Q: 如何创建新的迁移脚本？

A: 按照当前时间创建文件名（如 `202510201430.sql`），添加必要的注释和 SQL 语句，使用 InnoDB 引擎。

### Q: 迁移脚本执行失败怎么办？

A:

1. 检查错误信息，修复脚本问题
2. 恢复数据库备份
3. 修正脚本后重新执行

### Q: 如何查看当前数据库已执行到哪个版本？

A: 建议创建版本管理表记录已执行的迁移脚本：

```sql
CREATE TABLE IF NOT EXISTS `db_migrations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `version` varchar(20) NOT NULL COMMENT '版本号（文件名）',
  `executed_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移版本记录';
```
