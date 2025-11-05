#!/usr/bin/env python3
"""
时间工具 MCP 服务器

提供时间相关的工具函数，包括：
- 获取当前时间
- 时区转换
- 时间格式化
- 时间计算
- 倒计时
- 节假日查询
"""

import asyncio
import logging
import sys
from datetime import datetime, timedelta, timezone
from typing import Dict
from zoneinfo import ZoneInfo

from mcp.server.fastmcp import FastMCP
from lunarcalendar import Converter, Solar, Lunar

# 配置日志输出到stderr，避免污染MCP的stdio通道
logging.basicConfig(
    level=logging.INFO,
    stream=sys.stderr,
    format='[MCP-Time] %(levelname)s: %(message)s'
)
logger = logging.getLogger(__name__)

# 创建FastMCP实例
mcp = FastMCP("时间工具")

# 常用时区
COMMON_TIMEZONES = {
    "北京": "Asia/Shanghai",
    "东京": "Asia/Tokyo",
    "首尔": "Asia/Seoul",
    "新加坡": "Asia/Singapore",
    "香港": "Asia/Hong_Kong",
    "台北": "Asia/Taipei",
    "伦敦": "Europe/London",
    "巴黎": "Europe/Paris",
    "柏林": "Europe/Berlin",
    "莫斯科": "Europe/Moscow",
    "纽约": "America/New_York",
    "洛杉矶": "America/Los_Angeles",
    "芝加哥": "America/Chicago",
    "多伦多": "America/Toronto",
    "悉尼": "Australia/Sydney",
    "墨尔本": "Australia/Melbourne",
    "奥克兰": "Pacific/Auckland",
    "UTC": "UTC"
}

# 节假日数据缓存（key: 年份, value: 节假日字典）
_holidays_cache: Dict[int, Dict[str, str]] = {}

# 中国法定节假日定义（基于农历和公历）
CHINESE_HOLIDAYS_DEFINITION = {
    # 公历固定节假日
    "fixed": [
        {"month": 1, "day": 1, "name": "元旦", "days": 1},
        {"month": 5, "day": 1, "name": "劳动节", "days": 1},
        {"month": 10, "day": 1, "name": "国庆节", "days": 3},
    ],
    # 农历节假日
    "lunar": [
        {"month": 1, "day": 1, "name": "春节", "days": 3},  # 春节（除夕+初一+初二）
        {"month": 5, "day": 5, "name": "端午节", "days": 1},
        {"month": 8, "day": 15, "name": "中秋节", "days": 1},
    ]
}


def calculate_qingming(year: int) -> datetime.date:
    """计算清明节日期（公历）- 精确算法
    
    清明节是二十四节气之一，使用天文算法计算
    基于寿星天文历算法
    """
    # 春分后第15天为清明
    # 使用改进的寿星公式
    if year >= 1900 and year <= 2100:
        # 基准值
        if year < 2000:
            # 1900-1999年
            base = 5.59
        else:
            # 2000-2100年  
            base = 4.81
        
        # 计算
        C = year % 100
        qingming_day = int(C * 0.2422 + base) - int(C / 4)
        
        # 特殊年份修正
        special_years = {
            2008: 4,  # 2008年是4月4日
        }
        
        if year in special_years:
            qingming_day = special_years[year]
        
        return datetime(year, 4, qingming_day).date()
    else:
        # 其他年份使用估算
        return datetime(year, 4, 5).date()


def calculate_work_days_adjustment(base_date: datetime.date, holiday_name: str, days: int) -> list:
    """计算调休补假日期
    
    根据节假日规则计算需要补假的工作日
    
    Args:
        base_date: 节假日基准日期
        holiday_name: 节假日名称
        days: 法定假期天数
        
    Returns:
        list: 补假日期列表（公历日期字符串）
    """
    adjusted_dates = []
    
    # 获取节假日起始日期的星期
    weekday = base_date.weekday()  # 0=周一, 6=周日
    
    # 调休规则：
    # 1. 如果节假日在周中，通常会连着周末一起放
    # 2. 如果占用了周末，会在前后的周六日补班
    
    if holiday_name == "春节":
        # 春节：除夕+初一+初二（3天） + 周末 = 通常7天
        # 如果除夕是周一-周五，会调休凑成7天
        days_to_add = 7 - days  # 需要额外的天数
        
        # 前面补假（除夕前）
        if weekday <= 4:  # 周一到周五
            for i in range(1, days_to_add + 1):
                adj_date = base_date - timedelta(days=i)
                if adj_date.weekday() >= 5:  # 周末
                    adjusted_dates.append(adj_date)
        
        # 后面补假
        for i in range(1, days_to_add + 1):
            adj_date = base_date + timedelta(days=days - 1 + i)
            if adj_date.weekday() >= 5:
                adjusted_dates.append(adj_date)
                
    elif holiday_name == "国庆节":
        # 国庆节：10.1-10.3（3天）+ 周末 = 通常7天
        days_to_add = 7 - days
        
        for i in range(1, days_to_add + 1):
            adj_date = base_date + timedelta(days=days - 1 + i)
            if adj_date.weekday() >= 5:
                adjusted_dates.append(adj_date)
                
    elif holiday_name in ["清明节", "劳动节", "端午节", "中秋节"]:
        # 单日节假日：通常连周末凑3天
        if weekday == 0:  # 周一
            # 连着上周末，补假上上周六
            adjusted_dates.append(base_date - timedelta(days=3))
        elif weekday == 4:  # 周五
            # 连着周末，3天假期
            pass  # 不需要补假
        elif weekday == 6:  # 周日
            # 补假周一
            adjusted_dates.append(base_date + timedelta(days=1))
        elif weekday in [1, 2, 3]:  # 周二、三、四
            # 可能前补或后补一天
            if weekday == 1:  # 周二，补前一个周一
                adjusted_dates.append(base_date - timedelta(days=1))
            else:  # 周三、四，补后面的周五
                adjusted_dates.append(base_date + timedelta(days=5 - weekday))
    
    return adjusted_dates


def generate_holidays_for_year(year: int) -> Dict[str, str]:
    """自动生成指定年份的节假日数据
    
    根据公历固定日期和农历日期自动计算，包括调休补假和节假日合并
    """
    holidays = {}
    holiday_dates = {}  # 记录每个节假日的日期范围：{节假日名: [开始日期, 结束日期]}
    
    try:
        # 1. 收集所有节假日的基础信息
        all_holidays_info = []
        
        # 公历固定节假日
        for holiday in CHINESE_HOLIDAYS_DEFINITION["fixed"]:
            base_date = datetime(year, holiday["month"], holiday["day"]).date()
            all_holidays_info.append({
                "name": holiday["name"],
                "base_date": base_date,
                "days": holiday["days"]
            })
        
        # 农历节假日
        for holiday in CHINESE_HOLIDAYS_DEFINITION["lunar"]:
            try:
                lunar = Lunar(year, holiday["month"], holiday["day"], False)
                solar = Converter.Lunar2Solar(lunar)
                base_date = datetime(solar.year, solar.month, solar.day).date()
                
                # 春节特殊处理：从除夕开始
                if holiday["name"] == "春节":
                    base_date = base_date - timedelta(days=1)
                
                all_holidays_info.append({
                    "name": holiday["name"],
                    "base_date": base_date,
                    "days": holiday["days"]
                })
            except Exception as e:
                logger.warning(f"计算{year}年农历节假日失败 ({holiday['name']}): {e}")
        
        # 清明节
        qingming_date = calculate_qingming(year)
        all_holidays_info.append({
            "name": "清明节",
            "base_date": qingming_date,
            "days": 1
        })
        
        # 按日期排序
        all_holidays_info.sort(key=lambda x: x["base_date"])
        
        # 2. 检测并合并相邻的节假日
        merged_holidays = []
        i = 0
        while i < len(all_holidays_info):
            current = all_holidays_info[i]
            current_end = current["base_date"] + timedelta(days=current["days"] - 1)
            
            # 检查是否与下一个节假日相邻（间隔<=4天，考虑周末）
            if i + 1 < len(all_holidays_info):
                next_holiday = all_holidays_info[i + 1]
                gap = (next_holiday["base_date"] - current_end).days - 1
                
                # 如果间隔<=4天，且至少一个是长假，考虑合并
                if gap <= 4 and (current["days"] >= 3 or next_holiday["days"] >= 3):
                    # 合并节假日
                    merged_name = f"{current['name']}+{next_holiday['name']}"
                    # 计算中间需要填充的天数
                    merged_days = (next_holiday["base_date"] - current["base_date"]).days + next_holiday["days"]
                    
                    merged_holidays.append({
                        "name": merged_name,
                        "original_names": [current["name"], next_holiday["name"]],
                        "base_date": current["base_date"],
                        "days": merged_days,
                        "is_merged": True
                    })
                    i += 2  # 跳过下一个，因为已合并
                    logger.info(f"合并节假日：{merged_name}，共{merged_days}天")
                    continue
            
            merged_holidays.append({
                "name": current["name"],
                "original_names": [current["name"]],
                "base_date": current["base_date"],
                "days": current["days"],
                "is_merged": False
            })
            i += 1
        
        # 3. 生成节假日日期
        for holiday_info in merged_holidays:
            base_date = holiday_info["base_date"]
            days = holiday_info["days"]
            name = holiday_info["name"]
            
            # 添加所有假期日期
            for i in range(days):
                date_key = (base_date + timedelta(days=i)).strftime("%Y-%m-%d")
                # 使用第一个节假日名称作为标签
                holidays[date_key] = holiday_info["original_names"][0]
            
            # 计算调休（合并后的长假需要更多调休）
            if holiday_info["is_merged"]:
                # 合并假期：确保凑够7-8天
                target_days = 8 if days >= 6 else 7
                adjusted_needed = target_days - days
                adjusted_count = 0  # 记录已添加的调休天数
                
                # 从前后找周末补假
                for offset in range(1, 8):
                    if adjusted_count >= adjusted_needed:
                        break
                    # 往前找
                    check_date = base_date - timedelta(days=offset)
                    if check_date.weekday() >= 5:  # 周末
                        check_key = check_date.strftime("%Y-%m-%d")
                        if check_key not in holidays:
                            holidays[check_key] = f"{holiday_info['original_names'][0]}（调休）"
                            adjusted_count += 1
                    
                    # 往后找
                    if adjusted_count >= adjusted_needed:
                        break
                    check_date = base_date + timedelta(days=days - 1 + offset)
                    if check_date.weekday() >= 5:
                        check_key = check_date.strftime("%Y-%m-%d")
                        if check_key not in holidays:
                            holidays[check_key] = f"{holiday_info['original_names'][0]}（调休）"
                            adjusted_count += 1
            else:
                # 单独假期：正常计算调休
                adjusted = calculate_work_days_adjustment(base_date, name, days)
                for adj_date in adjusted:
                    adj_key = adj_date.strftime("%Y-%m-%d")
                    if adj_key not in holidays:
                        holidays[adj_key] = f"{name}（调休）"
        
        logger.info(f"自动生成{year}年节假日数据，共{len(holidays)}天（含调休）")
        
    except Exception as e:
        logger.error(f"生成{year}年节假日失败: {e}")
    
    return holidays


async def get_holidays_for_year(year: int) -> Dict[str, str]:
    """获取指定年份的节假日数据
    
    优先使用官方数据，回退到自动计算
    
    说明：
    - 自动计算基于农历和算法，提供参考
    - 调休安排为智能推测，可能与实际不符
    - 准确数据以国务院办公厅通知为准
    """
    
    # 1. 检查缓存
    if year in _holidays_cache:
        return _holidays_cache[year]
    
    # 2. 尝试从API获取官方数据
    try:
        import httpx
        async with httpx.AsyncClient(timeout=3.0) as client:
            url = f"http://timor.tech/api/holiday/year/{year}"
            response = await client.get(url)
            
            if response.status_code == 200:
                data = response.json()
                holidays = {}
                
                if "holiday" in data:
                    for date_str, info in data["holiday"].items():
                        if info.get("holiday", False):
                            holidays[date_str] = info.get("name", "节假日")
                
                if holidays:
                    logger.info(f"使用API获取{year}年官方节假日数据，共{len(holidays)}天")
                    _holidays_cache[year] = holidays
                    return holidays
    except (ImportError, httpx.HTTPError, httpx.TimeoutException, Exception) as e:
        pass  # API失败时静默回退
    
    # 3. 回退：自动生成节假日（基于农历计算+智能合并+调休）
    logger.info(f"回退，使用自动计算生成{year}年节假日数据（参考）")
    holidays = generate_holidays_for_year(year)
    
    # 4. 缓存结果
    _holidays_cache[year] = holidays
    
    return holidays


@mcp.tool()
async def get_current_time(timezone_name: str = "Asia/Shanghai", format_str: str = "%Y-%m-%d %H:%M:%S") -> str:
    """获取指定时区的当前时间
    
    Args:
        timezone_name: 时区名称，如 "Asia/Shanghai", "America/New_York" 等
                      也可以使用中文城市名，如 "北京", "纽约" 等
        format_str: 时间格式字符串，默认 "%Y-%m-%d %H:%M:%S"
    
    Returns:
        str: 格式化后的时间字符串
    """
    logger.info(f"获取当前时间: timezone={timezone_name}, format={format_str}")
    
    try:
        # 如果是中文名称，转换为时区标识
        tz_id = COMMON_TIMEZONES.get(timezone_name, timezone_name)
        
        logger.info(f"尝试使用时区: {tz_id}")
        
        # 获取指定时区的当前时间
        try:
            tz = ZoneInfo(tz_id)
        except Exception as tz_error:
            # 如果时区不存在，尝试使用默认时区
            logger.warning(f"时区 {tz_id} 不可用: {tz_error}, 使用UTC时区")
            if timezone_name in COMMON_TIMEZONES:
                return f"❌ 时区数据错误: {str(tz_error)}\n\n💡 建议：请确保已安装 tzdata 包: `pip install tzdata`"
            else:
                return f"❌ 未知的时区: {timezone_name}\n\n可用的时区: {', '.join(COMMON_TIMEZONES.keys())}"
        
        now = datetime.now(tz)
        
        # 格式化时间
        time_str = now.strftime(format_str)
        
        # 获取星期几
        weekday_cn = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
        weekday = weekday_cn[now.weekday()]
        
        result = f"""🕐 当前时间

**时区**: {timezone_name} ({tz_id})
**时间**: {time_str}
**星期**: {weekday}
**时间戳**: {int(now.timestamp())}

💡 其他常用格式：
- ISO格式: {now.isoformat()}
- 12小时制: {now.strftime('%Y-%m-%d %I:%M:%S %p')}
- 简短格式: {now.strftime('%m/%d %H:%M')}
"""
        return result
        
    except Exception as e:
        logger.error(f"获取时间失败: {e}", exc_info=True)
        return f"❌ 获取时间失败: {str(e)}\n\n可用的时区: {', '.join(COMMON_TIMEZONES.keys())}"


@mcp.tool()
async def convert_timezone(time_str: str, from_tz: str, to_tz: str, input_format: str = "%Y-%m-%d %H:%M:%S") -> str:
    """时区转换
    
    将时间从一个时区转换到另一个时区
    
    Args:
        time_str: 时间字符串，如 "2025-10-18 14:30:00"
        from_tz: 源时区，如 "Asia/Shanghai" 或 "北京"
        to_tz: 目标时区，如 "America/New_York" 或 "纽约"
        input_format: 输入时间的格式，默认 "%Y-%m-%d %H:%M:%S"
    
    Returns:
        str: 转换后的时间信息
    """
    logger.info(f"时区转换: {time_str} from {from_tz} to {to_tz}")
    
    try:
        # 转换时区名称
        from_tz_id = COMMON_TIMEZONES.get(from_tz, from_tz)
        to_tz_id = COMMON_TIMEZONES.get(to_tz, to_tz)
        
        # 解析时间
        naive_time = datetime.strptime(time_str, input_format)
        
        # 设置源时区
        from_tz_obj = ZoneInfo(from_tz_id)
        source_time = naive_time.replace(tzinfo=from_tz_obj)
        
        # 转换到目标时区
        to_tz_obj = ZoneInfo(to_tz_id)
        target_time = source_time.astimezone(to_tz_obj)
        
        # 计算时差
        offset = target_time.utcoffset().total_seconds() / 3600
        
        result = f"""🌍 时区转换

**源时区**: {from_tz} ({from_tz_id})
**源时间**: {source_time.strftime('%Y-%m-%d %H:%M:%S %Z')}

**目标时区**: {to_tz} ({to_tz_id})
**目标时间**: {target_time.strftime('%Y-%m-%d %H:%M:%S %Z')}

**时差**: UTC{offset:+.1f}
**ISO格式**: {target_time.isoformat()}
"""
        return result
        
    except Exception as e:
        logger.error(f"时区转换失败: {e}")
        return f"❌ 时区转换失败: {str(e)}"


@mcp.tool()
async def time_calculate(base_time: str = "now", 
                        days: int = 0, 
                        hours: int = 0, 
                        minutes: int = 0,
                        seconds: int = 0,
                        timezone_name: str = "Asia/Shanghai") -> str:
    """时间计算
    
    在基准时间上加减时间
    
    Args:
        base_time: 基准时间，"now" 表示当前时间，或时间字符串如 "2025-10-18 14:00:00"
        days: 要加减的天数（正数为加，负数为减）
        hours: 要加减的小时数
        minutes: 要加减的分钟数
        seconds: 要加减的秒数
        timezone_name: 时区名称
    
    Returns:
        str: 计算后的时间信息
    """
    logger.info(f"时间计算: base={base_time}, days={days}, hours={hours}, minutes={minutes}, seconds={seconds}")
    
    try:
        # 获取时区
        tz_id = COMMON_TIMEZONES.get(timezone_name, timezone_name)
        tz = ZoneInfo(tz_id)
        
        # 获取基准时间
        if base_time.lower() == "now":
            base_dt = datetime.now(tz)
        else:
            base_dt = datetime.strptime(base_time, "%Y-%m-%d %H:%M:%S").replace(tzinfo=tz)
        
        # 计算时间差
        delta = timedelta(days=days, hours=hours, minutes=minutes, seconds=seconds)
        result_dt = base_dt + delta
        
        # 计算实际差值
        total_seconds = delta.total_seconds()
        total_hours = total_seconds / 3600
        total_days = total_hours / 24
        
        result = f"""⏰ 时间计算

**基准时间**: {base_dt.strftime('%Y-%m-%d %H:%M:%S')}
**变化量**: {days}天 {hours}小时 {minutes}分钟 {seconds}秒
**结果时间**: {result_dt.strftime('%Y-%m-%d %H:%M:%S')}

**统计**:
- 总共: {abs(total_days):.2f}天 / {abs(total_hours):.2f}小时
- 星期: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][result_dt.weekday()]}
- 时间戳: {int(result_dt.timestamp())}
"""
        return result
        
    except Exception as e:
        logger.error(f"时间计算失败: {e}")
        return f"❌ 时间计算失败: {str(e)}"


@mcp.tool()
async def countdown_to(target_date: str, target_name: str = "目标日期", timezone_name: str = "Asia/Shanghai") -> str:
    """倒计时计算
    
    计算距离目标日期还有多久
    
    Args:
        target_date: 目标日期，格式 "YYYY-MM-DD" 或 "YYYY-MM-DD HH:MM:SS"
        target_name: 目标事件名称，如 "春节", "考试" 等
        timezone_name: 时区名称
    
    Returns:
        str: 倒计时信息
    """
    logger.info(f"倒计时: target={target_date}, name={target_name}")
    
    try:
        # 获取时区
        tz_id = COMMON_TIMEZONES.get(timezone_name, timezone_name)
        tz = ZoneInfo(tz_id)
        
        # 解析目标日期
        if len(target_date) == 10:  # YYYY-MM-DD
            target_dt = datetime.strptime(target_date, "%Y-%m-%d").replace(hour=0, minute=0, second=0, tzinfo=tz)
        else:  # YYYY-MM-DD HH:MM:SS
            target_dt = datetime.strptime(target_date, "%Y-%m-%d %H:%M:%S").replace(tzinfo=tz)
        
        # 当前时间
        now = datetime.now(tz)
        
        # 计算差值
        delta = target_dt - now
        
        if delta.total_seconds() < 0:
            # 已过期
            abs_delta = now - target_dt
            days = abs_delta.days
            hours, remainder = divmod(abs_delta.seconds, 3600)
            minutes, seconds = divmod(remainder, 60)
            
            result = f"""⏳ 倒计时

**事件**: {target_name}
**目标时间**: {target_dt.strftime('%Y-%m-%d %H:%M:%S')}
**当前时间**: {now.strftime('%Y-%m-%d %H:%M:%S')}

⚠️  **已过期** {days}天 {hours}小时 {minutes}分钟 {seconds}秒
"""
        else:
            # 未过期
            days = delta.days
            hours, remainder = divmod(delta.seconds, 3600)
            minutes, seconds = divmod(remainder, 60)
            
            # 计算百分比（假设从30天前开始）
            total_days = 30
            progress = max(0, min(100, (30 - days) / total_days * 100))
            
            result = f"""⏳ 倒计时到 {target_name}

**目标时间**: {target_dt.strftime('%Y-%m-%d %H:%M:%S')}
**当前时间**: {now.strftime('%Y-%m-%d %H:%M:%S')}

⏰ **还有**: {days}天 {hours}小时 {minutes}分钟 {seconds}秒

📊 **统计**:
- 总小时数: {delta.total_seconds() / 3600:.1f}小时
- 总天数: {delta.total_seconds() / 86400:.2f}天
- 目标星期: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][target_dt.weekday()]}
"""
        return result
        
    except Exception as e:
        logger.error(f"倒计时计算失败: {e}")
        return f"❌ 倒计时计算失败: {str(e)}"


@mcp.tool()
async def is_holiday(date_str: str = "today", timezone_name: str = "Asia/Shanghai") -> str:
    """查询是否为节假日
    
    查询指定日期是否为中国法定节假日
    
    Args:
        date_str: 日期字符串，格式 "YYYY-MM-DD"，或 "today" 表示今天
        timezone_name: 时区名称
    
    Returns:
        str: 节假日信息
    """
    logger.info(f"查询节假日: date={date_str}")
    
    try:
        # 获取时区
        tz_id = COMMON_TIMEZONES.get(timezone_name, timezone_name)
        tz = ZoneInfo(tz_id)
        
        # 获取日期
        if date_str.lower() == "today":
            check_date = datetime.now(tz).date()
        else:
            check_date = datetime.strptime(date_str, "%Y-%m-%d").date()
        
        date_key = check_date.strftime("%Y-%m-%d")
        year = check_date.year
        
        # 获取该年份的节假日数据（自动计算）
        holidays = await get_holidays_for_year(year)
        
        # 检查是否为节假日
        if date_key in holidays:
            holiday_name = holidays[date_key]
            result = f"""🎉 节假日查询

**日期**: {date_key}
**星期**: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][check_date.weekday()]}

✅ **是法定节假日**: {holiday_name}

💡 享受假期吧！

⚠️  数据来源：{'官方' if len(holidays) > 15 else '自动计算（参考）'}
"""
        else:
            # 检查是否为周末
            weekday = check_date.weekday()
            if weekday >= 5:  # 周六或周日
                result = f"""📅 日期查询

**日期**: {date_key}
**星期**: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][weekday]}

⚠️  不是法定节假日，但是周末

💡 可能需要调休，请查看具体通知
"""
            else:
                result = f"""📅 日期查询

**日期**: {date_key}
**星期**: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][weekday]}

❌ 不是节假日，是工作日

💡 好好工作/学习！
"""
        
        # 列出即将到来的节假日（当年和次年）
        upcoming = []
        
        # 先查当年剩余的节假日
        for holiday_date, holiday_name in sorted(holidays.items()):
            holiday_dt = datetime.strptime(holiday_date, "%Y-%m-%d").date()
            if holiday_dt > check_date:
                upcoming.append(f"- {holiday_date}: {holiday_name}")
                if len(upcoming) >= 3:
                    break
        
        # 如果不足3个，查询下一年的
        if len(upcoming) < 3:
            next_year_holidays = await get_holidays_for_year(year + 1)
            for holiday_date, holiday_name in sorted(next_year_holidays.items()):
                upcoming.append(f"- {holiday_date}: {holiday_name}")
                if len(upcoming) >= 3:
                    break
        
        if upcoming:
            result += f"\n**即将到来的节假日**:\n" + "\n".join(upcoming)
        
        return result
        
    except Exception as e:
        logger.error(f"查询节假日失败: {e}")
        return f"❌ 查询失败: {str(e)}"


@mcp.tool()
async def get_holiday_calendar(year: int = 2025) -> str:
    """获取全年放假日历
    
    显示指定年份的完整节假日安排，包括调休
    
    Args:
        year: 年份，如 2025
    
    Returns:
        str: 全年放假日历
    """
    logger.info(f"获取{year}年放假日历")
    
    try:
        # 获取节假日数据
        holidays = await get_holidays_for_year(year)
        
        if not holidays:
            return f"❌ 暂无{year}年节假日数据"
        
        # 按节假日分组
        holiday_groups = {}
        for date_str, name in sorted(holidays.items()):
            # 去掉（调休）后缀作为分组键
            group_name = name.replace("（调休）", "")
            if group_name not in holiday_groups:
                holiday_groups[group_name] = {"holidays": [], "adjusted": []}
            
            if "（调休）" in name:
                holiday_groups[group_name]["adjusted"].append(date_str)
            else:
                holiday_groups[group_name]["holidays"].append(date_str)
        
        # 生成日历
        result = f"""📅 {year}年中国法定节假日日历

总计：{len(holidays)}天（含调休）

"""
        
        for holiday_name in ["元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节"]:
            if holiday_name in holiday_groups:
                group = holiday_groups[holiday_name]
                result += f"**{holiday_name}**\n"
                
                if group["holidays"]:
                    dates = group["holidays"]
                    if len(dates) == 1:
                        result += f"  放假：{dates[0]}\n"
                    else:
                        result += f"  放假：{dates[0]} 至 {dates[-1]} ({len(dates)}天)\n"
                
                if group["adjusted"]:
                    adj_dates = ", ".join(group["adjusted"])
                    result += f"  调休：{adj_dates}\n"
                
                result += "\n"
        
        # 检查数据来源
        is_official = year in _holidays_cache and len(holidays) > 15  # 官方数据通常更完整
        
        result += f"""💡 说明：
- 数据来源：{'官方API（准确）' if is_official else '自动计算（参考）'}
- {'实际放假安排已由国务院办公厅发布' if is_official else '⚠️ 调休安排为智能推测，以国务院通知为准'}
"""
        
        return result
        
    except Exception as e:
        logger.error(f"获取放假日历失败: {e}")
        return f"❌ 获取失败: {str(e)}"


@mcp.tool()
async def list_timezones() -> str:
    """列出常用时区
    
    返回常用城市的时区列表
    
    Returns:
        str: 时区列表
    """
    logger.info("列出常用时区")
    
    result = "🌍 常用时区列表\n\n"
    
    # 按地区分组
    regions = {
        "亚洲": ["北京", "东京", "首尔", "新加坡", "香港", "台北"],
        "欧洲": ["伦敦", "巴黎", "柏林", "莫斯科"],
        "美洲": ["纽约", "洛杉矶", "芝加哥", "多伦多"],
        "大洋洲": ["悉尼", "墨尔本", "奥克兰"],
        "其他": ["UTC"]
    }
    
    for region, cities in regions.items():
        result += f"**{region}**:\n"
        for city in cities:
            tz_id = COMMON_TIMEZONES[city]
            result += f"- {city} ({tz_id})\n"
        result += "\n"
    
    result += "💡 使用城市中文名或时区标识都可以\n"
    result += "💡 例如: get_current_time('北京') 或 get_current_time('Asia/Shanghai')"
    
    return result


@mcp.tool()
async def format_timestamp(timestamp: int, timezone_name: str = "Asia/Shanghai") -> str:
    """时间戳转换
    
    将Unix时间戳转换为可读的时间字符串
    
    Args:
        timestamp: Unix时间戳（秒）
        timezone_name: 时区名称
    
    Returns:
        str: 格式化的时间信息
    """
    logger.info(f"时间戳转换: {timestamp}")
    
    try:
        # 获取时区
        tz_id = COMMON_TIMEZONES.get(timezone_name, timezone_name)
        tz = ZoneInfo(tz_id)
        
        # 转换时间戳
        dt = datetime.fromtimestamp(timestamp, tz=tz)
        
        result = f"""🕐 时间戳转换

**时间戳**: {timestamp}
**时区**: {timezone_name} ({tz_id})

**标准格式**: {dt.strftime('%Y-%m-%d %H:%M:%S')}
**ISO格式**: {dt.isoformat()}
**12小时制**: {dt.strftime('%Y-%m-%d %I:%M:%S %p')}
**星期**: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][dt.weekday()]}

**相对时间**: {format_relative_time(timestamp)}
"""
        return result
        
    except Exception as e:
        logger.error(f"时间戳转换失败: {e}")
        return f"❌ 转换失败: {str(e)}"


def format_relative_time(timestamp: int) -> str:
    """格式化相对时间"""
    now = datetime.now().timestamp()
    diff = now - timestamp
    
    if diff < 0:
        return "未来时间"
    elif diff < 60:
        return f"{int(diff)}秒前"
    elif diff < 3600:
        return f"{int(diff/60)}分钟前"
    elif diff < 86400:
        return f"{int(diff/3600)}小时前"
    elif diff < 604800:
        return f"{int(diff/86400)}天前"
    else:
        return f"{int(diff/604800)}周前"


@mcp.tool()
async def get_lunar_date(date_str: str = "today", timezone_name: str = "Asia/Shanghai") -> str:
    """获取农历日期
    
    将公历日期转换为农历日期，并显示生肖、天干地支等信息
    
    Args:
        date_str: 公历日期字符串，格式 "YYYY-MM-DD"，或 "today" 表示今天
        timezone_name: 时区名称
    
    Returns:
        str: 农历日期信息
    """
    logger.info(f"获取农历日期: date={date_str}")
    
    try:
        # 获取时区
        tz_id = COMMON_TIMEZONES.get(timezone_name, timezone_name)
        tz = ZoneInfo(tz_id)
        
        # 获取日期
        if date_str.lower() == "today":
            check_date = datetime.now(tz).date()
        else:
            check_date = datetime.strptime(date_str, "%Y-%m-%d").date()
        
        # 转换为农历
        solar = Solar(check_date.year, check_date.month, check_date.day)
        lunar = Converter.Solar2Lunar(solar)
        
        # 获取农历信息
        lunar_year = lunar.year
        lunar_month = lunar.month
        lunar_day = lunar.day
        is_leap = lunar.isleap
        
        # 天干地支
        gan = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸']
        zhi = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']
        shengxiao = ['鼠', '牛', '虎', '兔', '龙', '蛇', '马', '羊', '猴', '鸡', '狗', '猪']
        
        year_gan_index = (lunar_year - 4) % 10
        year_zhi_index = (lunar_year - 4) % 12
        year_ganzhi = f"{gan[year_gan_index]}{zhi[year_zhi_index]}"
        year_shengxiao = shengxiao[year_zhi_index]
        
        # 月份名称
        month_names = ['正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊']
        month_name = f"{'闰' if is_leap else ''}{month_names[lunar_month-1]}月"
        
        # 日期名称
        day_names = ['初一', '初二', '初三', '初四', '初五', '初六', '初七', '初八', '初九', '初十',
                    '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十',
                    '廿一', '廿二', '廿三', '廿四', '廿五', '廿六', '廿七', '廿八', '廿九', '三十']
        day_name = day_names[lunar_day-1] if lunar_day <= 30 else '三十'
        
        result = f"""🏮 农历日期查询

**公历**: {check_date.strftime('%Y年%m月%d日')} {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][check_date.weekday()]}

**农历**: {lunar_year}年{month_name}{day_name}
**天干地支**: {year_ganzhi}年
**生肖**: {year_shengxiao}年

💡 完整表述: 农历{year_ganzhi}{year_shengxiao}年{month_name}{day_name}
"""
        
        # 检查是否为重要节日
        traditional_festivals = get_traditional_festival(lunar_month, lunar_day, is_leap)
        if traditional_festivals:
            result += f"\n🎊 **传统节日**: {traditional_festivals}"
        
        return result
        
    except Exception as e:
        logger.error(f"获取农历日期失败: {e}")
        return f"❌ 获取农历日期失败: {str(e)}"


@mcp.tool()
async def lunar_to_solar(lunar_year: int, lunar_month: int, lunar_day: int, is_leap: bool = False) -> str:
    """农历转公历
    
    将农历日期转换为公历日期
    
    Args:
        lunar_year: 农历年份，如 2025
        lunar_month: 农历月份，1-12
        lunar_day: 农历日期，1-30
        is_leap: 是否为闰月，默认False
    
    Returns:
        str: 转换后的公历日期
    """
    logger.info(f"农历转公历: {lunar_year}-{lunar_month}-{lunar_day} (闰月: {is_leap})")
    
    try:
        # 转换为公历
        lunar = Lunar(lunar_year, lunar_month, lunar_day, is_leap)
        solar = Converter.Lunar2Solar(lunar)
        
        # 创建日期对象
        solar_date = datetime(solar.year, solar.month, solar.day).date()
        
        # 月份名称
        month_names = ['正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊']
        month_name = f"{'闰' if is_leap else ''}{month_names[lunar_month-1]}月"
        
        # 日期名称
        day_names = ['初一', '初二', '初三', '初四', '初五', '初六', '初七', '初八', '初九', '初十',
                    '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十',
                    '廿一', '廿二', '廿三', '廿四', '廿五', '廿六', '廿七', '廿八', '廿九', '三十']
        day_name = day_names[lunar_day-1] if lunar_day <= 30 else '三十'
        
        result = f"""🌙 农历转公历

**农历**: {lunar_year}年{month_name}{day_name}

**公历**: {solar_date.strftime('%Y年%m月%d日')}
**星期**: {['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'][solar_date.weekday()]}

💡 格式化: {solar_date.strftime('%Y-%m-%d')}
"""
        return result
        
    except Exception as e:
        logger.error(f"农历转公历失败: {e}")
        return f"❌ 转换失败: {str(e)}\n\n请检查输入的农历日期是否正确"


def get_traditional_festival(lunar_month: int, lunar_day: int, is_leap: bool) -> str:
    """获取传统节日名称"""
    if is_leap:
        return ""
    
    festivals = {
        (1, 1): "春节",
        (1, 15): "元宵节",
        (2, 2): "龙抬头",
        (5, 5): "端午节",
        (7, 7): "七夕节",
        (7, 15): "中元节",
        (8, 15): "中秋节",
        (9, 9): "重阳节",
        (10, 1): "寒衣节",
        (10, 15): "下元节",
        (12, 8): "腊八节",
        (12, 23): "小年",
    }
    
    return festivals.get((lunar_month, lunar_day), "")


if __name__ == "__main__":
    try:
        logger.info("正在启动时间工具MCP服务器...")
        
        # FastMCP提供了同步的run方法，自动处理asyncio事件循环
        mcp.run()
        
    except KeyboardInterrupt:
        logger.info("服务器已停止")
    except Exception as e:
        logger.error(f"启动失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
