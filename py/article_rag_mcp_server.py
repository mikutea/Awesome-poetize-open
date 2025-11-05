"""
POETIZE文章知识库 MCP 服务器
提供文章搜索、摘要、分块等功能

主要功能：
- 文章搜索
- 文章摘要
- 文章分块
- 文章对比
- 文章资源访问
- 文章统计
"""
import asyncio
import json
import logging
import sys
import os
from typing import List
from fastmcp import FastMCP, Context
import httpx
from config import JAVA_BACKEND_URL

# 设置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 从环境变量或配置文件读取后端URL
BACKEND_URL = JAVA_BACKEND_URL

# 创建FastMCP实例
mcp = FastMCP("POETIZE文章知识库")


def chunk_text(text: str, chunk_size: int = 2000, overlap: int = 200) -> List[str]:
    """
    将长文本分割成重叠的chunks
    
    Args:
        text: 要分割的文本
        chunk_size: 每个chunk的大小（字符数）
        overlap: chunk之间的重叠部分
    
    Returns:
        List[str]: 分割后的文本块列表
    """
    if len(text) <= chunk_size:
        return [text]
    
    chunks = []
    start = 0
    
    while start < len(text):
        end = start + chunk_size
        
        # 如果不是最后一个chunk，尝试在句子边界处分割
        if end < len(text):
            # 寻找最近的句子结束符
            for delimiter in ['。', '！', '？', '\n\n', '\n', '，', ' ']:
                pos = text.rfind(delimiter, start, end)
                if pos != -1:
                    end = pos + 1
                    break
        
        chunks.append(text[start:end].strip())
        start = end - overlap if end < len(text) else end
    
    return chunks


def extract_summary(content: str, max_length: int = 500) -> str:
    """
    从文章内容中提取摘要
    优先使用前几段，如果太短则使用开头部分
    """
    # 按段落分割
    paragraphs = [p.strip() for p in content.split('\n\n') if p.strip()]
    
    if not paragraphs:
        return content[:max_length]
    
    summary = []
    current_length = 0
    
    for para in paragraphs[:5]:  # 最多取前5段
        if current_length + len(para) > max_length:
            break
        summary.append(para)
        current_length += len(para)
    
    result = '\n\n'.join(summary)
    
    if len(result) < 100 and content:  # 如果太短，直接截取开头
        result = content[:max_length]
    
    return result


@mcp.tool()
async def search_and_summarize(keyword: str, max_articles: int = 5, ctx: Context = None) -> str:
    """
    智能搜索并总结文章（RAG增强版）
    
    这个工具会：
    1. 搜索相关文章
    2. 获取文章摘要
    3. 返回结构化的搜索结果，便于AI进行总结
    
    Args:
        keyword: 搜索关键词
        max_articles: 最多返回的文章数量，默认5篇
        ctx: MCP上下文（自动注入）
    
    Returns:
        str: 结构化的文章摘要信息
    """
    
    # 使用Context进行进度报告
    if ctx:
        await ctx.info(f"正在搜索关于「{keyword}」的文章...")
    
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            # 智能关键词处理：尝试多种变体
            import re
            search_keywords = [keyword]
            
            # 如果关键词中没有空格，尝试添加空格的版本（例如：Vue3 -> Vue 3）
            if re.match(r'^[A-Za-z]+\d+$', keyword):
                keyword_with_space = re.sub(r'([A-Za-z]+)(\d+)', r'\1 \2', keyword)
                search_keywords.append(keyword_with_space)
            
            # 如果关键词有空格，也尝试无空格的版本
            if ' ' in keyword:
                keyword_without_space = keyword.replace(' ', '')
                search_keywords.append(keyword_without_space)
                
                # 将空格分隔的关键词拆分成独立的词进行搜索
                # 例如："Vue React" -> ["Vue", "React"]
                split_keywords = [k.strip() for k in keyword.split() if k.strip()]
                if len(split_keywords) > 1:
                    search_keywords.extend(split_keywords)
            
            # 尝试各个关键词变体，收集结果
            all_articles = []
            seen_ids = set()
            
            for search_key in search_keywords:
                request_body = {
                    "current": 1,
                    "size": max_articles,
                    "searchKey": search_key
                }
                
                response = await client.post(
                    f"{BACKEND_URL}/article/listArticle",
                    json=request_body
                )
                
                if response.status_code != 200:
                    logger.warning(f"搜索关键词 '{search_key}' 失败: HTTP {response.status_code}")
                    continue
                
                data = response.json()
                
                if data.get("code") != 200:
                    logger.warning(f"搜索关键词 '{search_key}' 失败: {data.get('message')}")
                    continue
                
                page_data = data.get("data", {})
                articles_batch = page_data.get("records", [])
                
                # 合并结果，去重
                for article in articles_batch:
                    article_id = article.get('id')
                    if article_id not in seen_ids:
                        seen_ids.add(article_id)
                        all_articles.append(article)
                
                # 如果已经找到足够的文章，提前退出
                if len(all_articles) >= max_articles:
                    break
            
            # 使用合并后的结果
            articles = all_articles[:max_articles]
            
            if not articles:
                return f"未找到关于「{keyword}」的文章。"
            
            if ctx:
                await ctx.info(f"找到 {len(articles)} 篇相关文章，正在获取内容...")
            
            # 获取每篇文章的详细内容
            article_summaries = []
            
            for i, article in enumerate(articles, 1):
                article_id = article.get('id')
                title = article.get('articleTitle', '无标题')
                
                if ctx:
                    await ctx.info(f"正在处理第 {i}/{len(articles)} 篇: {title}")
                
                try:
                    # 获取文章详情
                    detail_response = await client.get(
                        f"{BACKEND_URL}/article/getArticleById",
                        params={"id": article_id}
                    )
                    
                    if detail_response.status_code == 200:
                        detail_data = detail_response.json()
                        
                        if detail_data.get("code") == 200:
                            article_detail = detail_data.get("data", {})
                            content = article_detail.get('articleContent', '')
                            
                            # 提取摘要（避免内容太长）
                            summary = extract_summary(content, max_length=800)
                            
                            article_summaries.append({
                                'id': article_id,
                                'title': title,
                                'sort_name': article.get('sortName', '未分类'),
                                'label_name': article.get('labelName', '无标签'),
                                'view_count': article.get('viewCount', 0),
                                'like_count': article.get('likeCount', 0),
                                'article_url': article.get('articleUrl', ''),
                                'summary': summary,
                                'content_length': len(content)
                            })
                
                except Exception as e:
                    logger.warning(f"获取文章 {article_id} 失败: {e}")
                    continue
            
            if not article_summaries:
                return "❌ 无法获取文章内容"
            
            # 构建结构化的返回结果
            result = f"🔍 关于「{keyword}」的搜索结果（共 {len(article_summaries)} 篇）：\n\n"
            result += "=" * 60 + "\n\n"
            
            for i, article_info in enumerate(article_summaries, 1):
                result += f"【文章 {i}】**{article_info['title']}**\n"
                result += f"📁 分类: {article_info['sort_name']} | 🏷️ 标签: {article_info['label_name']}\n"
                result += f"👀 浏览: {article_info['view_count']} | ❤️ 点赞: {article_info['like_count']}\n"
                result += f"📄 内容长度: {article_info['content_length']} 字符\n"
                result += f"🆔 ID: {article_info['id']}\n"
                if article_info.get('article_url'):
                    result += f"🔗 链接: {article_info['article_url']}\n"
                result += "\n"
                result += "**内容摘要：**\n"
                result += article_info['summary']
                result += "\n\n" + "-" * 60 + "\n\n"
            
            result += "💡 提示：以上是文章的关键摘要，你可以基于这些内容进行总结和分析。\n"
            result += "如需查看完整内容，使用 get_article_with_chunks(article_id) 工具。"
            
            if ctx:
                await ctx.info("✅ 内容准备完成，可以开始总结了")
            
            return result
            
    except httpx.TimeoutException:
        return "❌ 请求超时，请稍后重试"
    except Exception as e:
        logger.error(f"智能搜索失败: {e}")
        return f"❌ 搜索失败: {str(e)}"


@mcp.tool()
async def get_article_with_chunks(article_id: int, chunk_size: int = 2000) -> str:
    """
    获取文章内容并分块返回（适合长文章）
    
    将长文章分割成多个重叠的chunks，避免超出AI上下文限制
    
    Args:
        article_id: 文章ID
        chunk_size: 每个chunk的大小（字符数），默认2000
    
    Returns:
        str: 分块后的文章内容
    """
    
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(
                f"{BACKEND_URL}/article/getArticleById",
                params={"id": article_id}
            )
            
            if response.status_code != 200:
                return f"❌ 获取文章失败: HTTP {response.status_code}"
            
            data = response.json()
            
            if data.get("code") != 200:
                error_msg = data.get("message", "未知错误")
                return f"❌ 获取文章失败: {error_msg}"
            
            article = data.get("data", {})
            
            if not article:
                return f"❌ 未找到ID为 {article_id} 的文章"
            
            title = article.get('articleTitle', '无标题')
            content = article.get('articleContent', '无内容')
            sort_name = article.get('sortName', '未分类')
            article_url = article.get('articleUrl', '')
            
            # 优先使用labelName字段，如果没有则尝试从labelList或label对象获取
            label_name = article.get('labelName')
            if not label_name:
                # 尝试从label对象获取
                label = article.get('label', {})
                if label:
                    label_name = label.get('labelName')
            if not label_name:
                # 尝试从labelList获取
                label_list = article.get('labelList', [])
                if label_list:
                    label_name = ', '.join([label.get('labelName', '') for label in label_list])
            labels = label_name if label_name else '无标签'
            
            # 分块处理
            chunks = chunk_text(content, chunk_size=chunk_size)
            
            result = f"""# {title}

---
**分类**: {sort_name} | **标签**: {labels}
**文章ID**: {article_id}
**内容长度**: {len(content)} 字符
**分块数量**: {len(chunks)} 块"""
            if article_url:
                result += f"\n**文章链接**: {article_url}"
            result += "\n---\n\n"
            
            if len(chunks) == 1:
                result += content
            else:
                result += f"📚 **注意**: 此文章较长，已分为 {len(chunks)} 个部分\n\n"
                
                for i, chunk in enumerate(chunks, 1):
                    result += f"### 【第 {i}/{len(chunks)} 部分】\n\n"
                    result += chunk
                    result += f"\n\n{'=' * 60}\n\n"
            
            return result
            
    except httpx.TimeoutException:
        return "❌ 请求超时，请稍后重试"
    except Exception as e:
        logger.error(f"获取文章内容失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def search_articles(keyword: str, limit: int = 10) -> str:
    """搜索网站文章（基础版）
    
    返回匹配的文章列表，包含标题、分类、标签等基本信息。
    
    Args:
        keyword: 搜索关键词
        limit: 返回结果数量限制，默认10篇
    """
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            # 智能关键词处理：尝试多种变体
            import re
            search_keywords = [keyword]
            
            # 如果关键词中没有空格，尝试添加空格的版本（例如：Vue3 -> Vue 3）
            # 匹配字母+数字的组合（如Vue3, React18等）
            if re.match(r'^[A-Za-z]+\d+$', keyword):
                # 在字母和数字之间插入空格
                keyword_with_space = re.sub(r'([A-Za-z]+)(\d+)', r'\1 \2', keyword)
                search_keywords.append(keyword_with_space)
            
            # 如果关键词有空格，也尝试无空格的版本
            if ' ' in keyword:
                keyword_without_space = keyword.replace(' ', '')
                search_keywords.append(keyword_without_space)
                
                # 将空格分隔的关键词拆分成独立的词进行搜索
                # 例如："Vue React" -> ["Vue", "React"]
                split_keywords = [k.strip() for k in keyword.split() if k.strip()]
                if len(split_keywords) > 1:
                    search_keywords.extend(split_keywords)
            
            # 尝试各个关键词变体
            all_articles = []
            seen_ids = set()
            
            for search_key in search_keywords:
                request_body = {
                    "current": 1,
                    "size": limit,
                    "searchKey": search_key
                }
                
                response = await client.post(
                    f"{BACKEND_URL}/article/listArticle",
                    json=request_body
                )
                
                if response.status_code != 200:
                    logger.warning(f"搜索关键词 '{search_key}' 失败: HTTP {response.status_code}")
                    continue
                
                data = response.json()
                
                if data.get("code") != 200:
                    logger.warning(f"搜索关键词 '{search_key}' 失败: {data.get('message')}")
                    continue
                
                page_data = data.get("data", {})
                articles = page_data.get("records", [])
                
                # 合并结果，去重
                for article in articles:
                    article_id = article.get('id')
                    if article_id not in seen_ids:
                        seen_ids.add(article_id)
                        all_articles.append(article)
                
                # 如果已经找到足够的文章，提前退出
                if len(all_articles) >= limit:
                    break
            
            # 使用合并后的结果
            articles = all_articles[:limit]
            
            if not articles:
                return f"未找到关于「{keyword}」的文章。\n\n💡 提示：试试其他关键词或查看所有分类。"
            
            # 优先使用records的长度，如果total为0但有记录，使用实际记录数
            total = page_data.get("total", 0)
            if total == 0 and len(articles) > 0:
                total = len(articles)
            
            result = f"找到 {total} 篇关于「{keyword}」的文章（显示前 {len(articles)} 篇）：\n\n"
            
            for i, article in enumerate(articles, 1):
                title = article.get('articleTitle', '无标题')
                article_id = article.get('id', '')
                sort_name = article.get('sortName', '未分类')
                label_name = article.get('labelName', '无标签')
                view_count = article.get('viewCount', 0)
                like_count = article.get('likeCount', 0)
                article_url = article.get('articleUrl', '')
                
                result += f"{i}. **{title}**\n"
                result += f"   📁 分类: {sort_name}\n"
                result += f"   🏷️ 标签: {label_name}\n"
                result += f"   👀 浏览: {view_count} | ❤️ 点赞: {like_count}\n"
                result += f"   🆔 ID: {article_id}\n"
                if article_url:
                    result += f"   🔗 链接: {article_url}\n"
                result += "\n"
            
            result += "💡 使用 search_and_summarize() 可以智能搜索并获取文章摘要，便于总结\n"
            result += "💡 使用 get_article_with_chunks(article_id) 查看完整内容（长文章会自动分块）"
            return result
            
    except Exception as e:
        logger.error(f"搜索文章失败: {e}")
        return f"❌ 搜索失败: {str(e)}"


@mcp.tool()
async def get_article_content(article_id: int) -> str:
    """获取文章完整内容（标准版）
    
    直接返回文章的完整内容，不分块。适合中短篇文章。
    如果文章很长，建议使用 get_article_with_chunks() 工具。
    
    Args:
        article_id: 文章ID
    
    Returns:
        str: 文章的完整Markdown格式内容
    """
    
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(
                f"{BACKEND_URL}/article/getArticleById",
                params={"id": article_id}
            )
            
            if response.status_code != 200:
                return f"❌ 获取文章失败: HTTP {response.status_code}"
            
            data = response.json()
            
            if data.get("code") != 200:
                error_msg = data.get("message", "未知错误")
                return f"❌ 获取文章失败: {error_msg}"
            
            article = data.get("data", {})
            
            if not article:
                return f"❌ 未找到ID为 {article_id} 的文章"
            
            title = article.get('articleTitle', '无标题')
            content = article.get('articleContent', '无内容')
            sort_name = article.get('sortName', '未分类')
            article_url = article.get('articleUrl', '')
            
            # 优先使用labelName字段，如果没有则尝试从labelList或label对象获取
            label_name = article.get('labelName')
            if not label_name:
                # 尝试从label对象获取
                label = article.get('label', {})
                if label:
                    label_name = label.get('labelName')
            if not label_name:
                # 尝试从labelList获取
                label_list = article.get('labelList', [])
                if label_list:
                    label_name = ', '.join([label.get('labelName', '') for label in label_list])
            labels = label_name if label_name else '无标签'
            
            view_count = article.get('viewCount', 0)
            like_count = article.get('likeCount', 0)
            create_time = article.get('createTime', '')
            
            result = f"""# {title}

---
**分类**: {sort_name}
**标签**: {labels}
**发布时间**: {create_time}
**浏览**: {view_count} | **点赞**: {like_count}"""
            if article_url:
                result += f"\n**文章链接**: {article_url}"
            result += f"""
---

{content}

---
文章ID: {article_id}
"""
            return result
            
    except httpx.TimeoutException:
        return "❌ 请求超时，请稍后重试"
    except Exception as e:
        logger.error(f"获取文章内容失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def list_categories() -> str:
    """列出所有文章分类
    
    获取网站所有文章分类及每个分类下的文章数量。
    
    Returns:
        str: 格式化的分类列表
    """
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(f"{BACKEND_URL}/webInfo/getSortInfo")
            
            if response.status_code != 200:
                return f"❌ 获取分类失败: HTTP {response.status_code}"
            
            data = response.json()
            
            if data.get("code") != 200:
                error_msg = data.get("message", "未知错误")
                return f"❌ 获取分类失败: {error_msg}"
            
            categories = data.get("data", [])
            
            if not categories:
                return "暂无分类"
            
            result = "📁 文章分类列表：\n\n"
            
            for i, cat in enumerate(categories, 1):
                sort_name = cat.get('sortName', '未命名')
                sort_description = cat.get('sortDescription', '')
                count = cat.get('countOfSort', 0)
                sort_id = cat.get('id', '')
                
                result += f"{i}. **{sort_name}** ({count}篇)\n"
                if sort_description:
                    result += f"   📝 {sort_description}\n"
                result += f"   🆔 ID: {sort_id}\n\n"
            
            result += "💡 使用 get_articles_by_category(category_id) 查看分类下的文章"
            return result
            
    except Exception as e:
        logger.error(f"获取分类失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def get_articles_by_category(category_id: int, page: int = 1, page_size: int = 10) -> str:
    """根据分类获取文章列表
    
    Args:
        category_id: 分类ID
        page: 页码，默认第1页
        page_size: 每页文章数，默认10篇
    
    Returns:
        str: 该分类下的文章列表
    """
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                f"{BACKEND_URL}/article/listArticle",
                json={
                    "current": page,
                    "size": page_size,
                    "sortId": category_id
                }
            )
            
            if response.status_code != 200:
                return f"❌ 获取文章失败: HTTP {response.status_code}"
            
            data = response.json()
            
            if data.get("code") != 200:
                error_msg = data.get("message", "未知错误")
                return f"❌ 获取文章失败: {error_msg}"
            
            page_data = data.get("data", {})
            records = page_data.get("records", [])
            total = page_data.get("total", 0)
            pages = page_data.get("pages", 1)
            
            if not records:
                return f"该分类暂无文章"
            
            result = f"📁 分类文章列表（第 {page}/{pages} 页，共 {total} 篇）：\n\n"
            
            for i, article in enumerate(records, 1):
                title = article.get('articleTitle', '无标题')
                article_id = article.get('id', '')
                view_count = article.get('viewCount', 0)
                like_count = article.get('likeCount', 0)
                create_time = article.get('createTime', '')
                
                result += f"{i}. **{title}**\n"
                result += f"   👀 {view_count} | ❤️ {like_count} | 📅 {create_time}\n"
                result += f"   🆔 ID: {article_id}\n\n"
            
            if page < pages:
                result += f"\n💡 使用 get_articles_by_category({category_id}, {page + 1}) 查看下一页"
            
            return result
            
    except Exception as e:
        logger.error(f"获取分类文章失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def get_article_statistics() -> str:
    """获取网站文章统计信息
    
    Returns:
        str: 文章数量、访问量等统计信息
    """
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(f"{BACKEND_URL}/webInfo/getWebInfo")
            
            if response.status_code != 200:
                return f"❌ 获取统计信息失败: HTTP {response.status_code}"
            
            data = response.json()
            
            if data.get("code") != 200:
                error_msg = data.get("message", "未知错误")
                return f"❌ 获取统计信息失败: {error_msg}"
            
            web_info = data.get("data", {})
            
            web_name = web_info.get('webName', 'Poetize')
            web_title = web_info.get('webTitle', '')
            history_all_count = web_info.get('historyAllCount', '0')
            article_count = web_info.get('articleCount', 0)
            
            result = f"""📊 **{web_name} 网站统计**

- 网站标题: {web_title}
- 文章总数: {article_count}篇
- 总访问量: {history_all_count}

💡 使用以下工具查看更多信息：
- list_categories() - 查看所有分类
- search_articles(keyword) - 搜索文章
- get_hot_articles() - 查看热门文章
"""
            return result
            
    except Exception as e:
        logger.error(f"获取统计信息失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def get_hot_articles(limit: int = 10) -> str:
    """获取热门文章列表
    
    根据智能热度算法排序，综合考虑浏览量、点赞数、评论数等因素。
    
    Args:
        limit: 返回文章数量，默认10篇
    
    Returns:
        str: 热门文章列表
    """
    
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(f"{BACKEND_URL}/article/getHotArticles")
            
            if response.status_code != 200:
                return f"❌ 获取热门文章失败: HTTP {response.status_code}"
            
            data = response.json()
            
            if data.get("code") != 200:
                error_msg = data.get("message", "未知错误")
                return f"❌ 获取热门文章失败: {error_msg}"
            
            articles = data.get("data", [])
            
            if not articles:
                return "暂无热门文章"
            
            articles = articles[:limit]
            
            result = f"🔥 热门文章TOP {len(articles)}：\n\n"
            
            for i, article in enumerate(articles, 1):
                title = article.get('articleTitle', '无标题')
                article_id = article.get('id', '')
                sort_name = article.get('sortName', '未分类')
                view_count = article.get('viewCount', 0)
                like_count = article.get('likeCount', 0)
                comment_count = article.get('commentCount', 0)
                article_url = article.get('articleUrl', '')
                
                result += f"{i}. **{title}**\n"
                result += f"   📁 {sort_name}\n"
                result += f"   👀 {view_count} | ❤️ {like_count} | 💬 {comment_count}\n"
                result += f"   🆔 ID: {article_id}\n"
                if article_url:
                    result += f"   🔗 链接: {article_url}\n"
                result += "\n"
            
            result += "💡 使用 get_article_content(article_id) 或 search_and_summarize() 查看详情"
            return result
            
    except Exception as e:
        logger.error(f"获取热门文章失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def compare_articles(article_ids: List[int], ctx: Context = None) -> str:
    """
    对比多篇文章的内容
    
    获取多篇文章的摘要，便于AI进行对比分析
    
    Args:
        article_ids: 要对比的文章ID列表（最多5篇）
        ctx: MCP上下文（自动注入）
    
    Returns:
        str: 结构化的对比信息
    """
    if len(article_ids) > 5:
        return "❌ 最多只能对比5篇文章"
    
    if len(article_ids) < 2:
        return "❌ 至少需要2篇文章才能进行对比"
    
    
    if ctx:
        await ctx.info(f"正在获取 {len(article_ids)} 篇文章进行对比...")
    
    try:
        async with httpx.AsyncClient(timeout=20.0) as client:
            articles_info = []
            
            for article_id in article_ids:
                try:
                    response = await client.get(
                        f"{BACKEND_URL}/article/getArticleById",
                        params={"id": article_id}
                    )
                    
                    if response.status_code == 200:
                        data = response.json()
                        
                        if data.get("code") == 200:
                            article = data.get("data", {})
                            content = article.get('articleContent', '')
                            
                            articles_info.append({
                                'id': article_id,
                                'title': article.get('articleTitle', '无标题'),
                                'sort_name': article.get('sortName', '未分类'),
                                'view_count': article.get('viewCount', 0),
                                'like_count': article.get('likeCount', 0),
                                'create_time': article.get('createTime', ''),
                                'summary': extract_summary(content, max_length=600),
                                'content_length': len(content)
                            })
                
                except Exception as e:
                    logger.warning(f"获取文章 {article_id} 失败: {e}")
                    continue
            
            if len(articles_info) < 2:
                return "❌ 无法获取足够的文章进行对比"
            
            result = f"📊 文章对比分析（共 {len(articles_info)} 篇）\n\n"
            result += "=" * 60 + "\n\n"
            
            for i, info in enumerate(articles_info, 1):
                result += f"### 【文章 {i}】{info['title']}\n\n"
                result += f"- **分类**: {info['sort_name']}\n"
                result += f"- **浏览**: {info['view_count']} | **点赞**: {info['like_count']}\n"
                result += f"- **发布时间**: {info['create_time']}\n"
                result += f"- **内容长度**: {info['content_length']} 字符\n"
                result += f"- **ID**: {info['id']}\n\n"
                result += "**内容摘要：**\n"
                result += info['summary']
                result += "\n\n" + "-" * 60 + "\n\n"
            
            result += "💡 提示：以上是各篇文章的关键信息，你可以基于这些内容进行对比分析。"
            
            if ctx:
                await ctx.info("✅ 对比准备完成")
            
            return result
            
    except Exception as e:
        logger.error(f"对比文章失败: {e}")
        return f"❌ 对比失败: {str(e)}"


@mcp.resource("article://{article_id}")
async def article_resource(article_id: int) -> str:
    """通过资源方式访问文章内容
    
    这是一个MCP Resource，可以被客户端直接读取。
    """
    
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(
                f"{BACKEND_URL}/article/getArticleById",
                params={"id": article_id}
            )
            
            if response.status_code == 200:
                data = response.json()
                return json.dumps(data.get("data", {}), ensure_ascii=False, indent=2)
            
            return json.dumps({"error": f"HTTP {response.status_code}"})
            
    except Exception as e:
        return json.dumps({"error": str(e)})


if __name__ == "__main__":
    try:
        logger.info("正在启动文章知识库MCP服务器...")
        
        # FastMCP提供了同步的run方法，自动处理asyncio事件循环
        mcp.run()
        
    except KeyboardInterrupt:
        logger.info("服务器已停止")
    except Exception as e:
        logger.error(f"启动失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
