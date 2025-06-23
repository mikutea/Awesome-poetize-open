"""
POETIZE博客系统 - 图片处理模块

主要功能：
- 图片格式自动转换 (JPG/PNG/ICO/WEBP)
- 图片自动压缩优化
- 图标尺寸自动调整
- 批量图片处理
- 图片质量优化

版本: 1.0.0
"""

import os
import io
import json
import logging
from PIL import Image, ImageOps
from typing import Tuple, Optional, Union, List, Dict
import base64
import hashlib
from pathlib import Path

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('image_processor')

# 图片处理配置
IMAGE_CONFIG = {
    # 支持的输入格式
    'SUPPORTED_INPUT_FORMATS': ['JPEG', 'JPG', 'PNG', 'GIF', 'BMP', 'TIFF', 'WEBP'],
    
    # 支持的输出格式
    'SUPPORTED_OUTPUT_FORMATS': ['JPEG', 'PNG', 'WEBP', 'ICO'],
    
    # 质量设置
    'QUALITY_SETTINGS': {
        'high': 95,
        'medium': 85,
        'low': 70,
        'icon': 90  # 图标专用质量
    },
    
    # 标准图标尺寸
    'ICON_SIZES': {
        'favicon': [(16, 16), (32, 32), (48, 48)],
        'apple_touch': [(180, 180)],
        'pwa_192': [(192, 192)],
        'pwa_512': [(512, 512)],
        'social': [(1200, 630)],  # Open Graph标准尺寸
        'logo': [(300, 300)]
    },
    
    # 压缩阈值
    'COMPRESSION_THRESHOLDS': {
        'large_file_size': 2 * 1024 * 1024,  # 2MB
        'medium_file_size': 500 * 1024,      # 500KB
        'small_file_size': 100 * 1024        # 100KB
    }
}

class ImageProcessor:
    """图片处理器类"""
    
    def __init__(self, data_dir: str = None):
        """
        初始化图片处理器
        :param data_dir: 数据目录路径
        """
        self.data_dir = data_dir or os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
        self.cache_dir = os.path.join(self.data_dir, 'image_cache')
        
        # 确保目录存在
        os.makedirs(self.cache_dir, exist_ok=True)
        
        logger.info(f"图片处理器初始化完成，缓存目录: {self.cache_dir}")
    
    def validate_image(self, image_data: bytes) -> Tuple[bool, str, Optional[Image.Image]]:
        """
        验证图片数据
        :param image_data: 图片二进制数据
        :return: (是否有效, 错误信息, PIL图片对象)
        """
        try:
            # 尝试打开图片
            img = Image.open(io.BytesIO(image_data))
            
            # 验证格式
            if img.format not in IMAGE_CONFIG['SUPPORTED_INPUT_FORMATS']:
                return False, f"不支持的图片格式: {img.format}", None
            
            # 验证尺寸
            if img.size[0] > 4096 or img.size[1] > 4096:
                return False, "图片尺寸过大，最大支持4096x4096像素", None
            
            if img.size[0] < 16 or img.size[1] < 16:
                return False, "图片尺寸过小，最小16x16像素", None
            
            # 验证文件大小
            if len(image_data) > 10 * 1024 * 1024:  # 10MB
                return False, "文件大小超过10MB限制", None
            
            logger.info(f"图片验证通过: {img.format}, {img.size}, {len(image_data)}字节")
            return True, "", img
            
        except Exception as e:
            logger.error(f"图片验证失败: {str(e)}")
            return False, f"图片格式错误: {str(e)}", None
    
    def auto_resize(self, img: Image.Image, target_type: str, max_size: Optional[Tuple[int, int]] = None) -> Image.Image:
        """
        自动调整图片尺寸
        :param img: PIL图片对象
        :param target_type: 目标类型 ('favicon', 'apple_touch', 'pwa_192', 'pwa_512', 'social', 'logo')
        :param max_size: 最大尺寸限制
        :return: 调整后的图片
        """
        try:
            # 获取目标尺寸
            target_sizes = IMAGE_CONFIG['ICON_SIZES'].get(target_type, [(256, 256)])
            target_size = target_sizes[0]  # 使用第一个尺寸作为标准
            
            # 如果指定了最大尺寸，使用更小的尺寸
            if max_size:
                target_size = (min(target_size[0], max_size[0]), min(target_size[1], max_size[1]))
            
            # 如果图片已经是目标尺寸，直接返回
            if img.size == target_size:
                logger.info(f"图片尺寸已符合要求: {img.size}")
                return img
            
            # 计算缩放比例，保持宽高比
            img_ratio = img.size[0] / img.size[1]
            target_ratio = target_size[0] / target_size[1]
            
            if img_ratio > target_ratio:
                # 图片更宽，以高度为准
                new_height = target_size[1]
                new_width = int(new_height * img_ratio)
            else:
                # 图片更高，以宽度为准
                new_width = target_size[0]
                new_height = int(new_width / img_ratio)
            
            # 先调整尺寸
            img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
            
            # 如果尺寸不完全匹配，进行中心裁剪
            if img.size != target_size:
                left = (img.size[0] - target_size[0]) // 2
                top = (img.size[1] - target_size[1]) // 2
                right = left + target_size[0]
                bottom = top + target_size[1]
                
                img = img.crop((left, top, right, bottom))
            
            logger.info(f"图片尺寸调整完成: {img.size}")
            return img
            
        except Exception as e:
            logger.error(f"图片尺寸调整失败: {str(e)}")
            return img  # 返回原图
    
    def auto_compress(self, img: Image.Image, target_format: str, quality: str = 'medium') -> bytes:
        """
        自动压缩图片
        :param img: PIL图片对象
        :param target_format: 目标格式 ('JPEG', 'PNG', 'WEBP', 'ICO')
        :param quality: 质量等级 ('high', 'medium', 'low', 'icon')
        :return: 压缩后的图片数据
        """
        try:
            # 获取质量设置
            quality_value = IMAGE_CONFIG['QUALITY_SETTINGS'].get(quality, 85)
            
            # 转换颜色模式
            if target_format == 'JPEG':
                # JPEG不支持透明度，转换为RGB
                if img.mode in ('RGBA', 'LA', 'P'):
                    # 创建白色背景
                    background = Image.new('RGB', img.size, (255, 255, 255))
                    if img.mode == 'P':
                        img = img.convert('RGBA')
                    background.paste(img, mask=img.split()[-1] if img.mode == 'RGBA' else None)
                    img = background
                elif img.mode != 'RGB':
                    img = img.convert('RGB')
            
            elif target_format in ('PNG', 'WEBP', 'ICO'):
                # 这些格式支持透明度
                if img.mode not in ('RGBA', 'RGB'):
                    img = img.convert('RGBA')
            
            # 保存到内存
            output = io.BytesIO()
            
            if target_format == 'ICO':
                # ICO格式特殊处理
                img.save(output, format='ICO', sizes=[(img.size[0], img.size[1])])
            elif target_format == 'PNG':
                # PNG使用optimize参数
                img.save(output, format='PNG', optimize=True)
            else:
                # JPEG和WEBP使用quality参数
                img.save(output, format=target_format, quality=quality_value, optimize=True)
            
            compressed_data = output.getvalue()
            output.close()
            
            logger.info(f"图片压缩完成: {target_format}, 质量={quality_value}, 大小={len(compressed_data)}字节")
            return compressed_data
            
        except Exception as e:
            logger.error(f"图片压缩失败: {str(e)}")
            raise
    
    def generate_multiple_sizes(self, img: Image.Image, target_type: str, output_format: str = 'PNG') -> Dict[str, bytes]:
        """
        生成多种尺寸的图标
        :param img: PIL图片对象
        :param target_type: 目标类型
        :param output_format: 输出格式
        :return: 尺寸->图片数据的字典
        """
        try:
            sizes = IMAGE_CONFIG['ICON_SIZES'].get(target_type, [(256, 256)])
            results = {}
            
            for size in sizes:
                # 调整尺寸
                resized_img = img.resize(size, Image.Resampling.LANCZOS)
                
                # 压缩
                compressed_data = self.auto_compress(resized_img, output_format, 'icon')
                
                size_key = f"{size[0]}x{size[1]}"
                results[size_key] = compressed_data
                
                logger.info(f"生成{size_key}尺寸图标: {len(compressed_data)}字节")
            
            return results
            
        except Exception as e:
            logger.error(f"生成多尺寸图标失败: {str(e)}")
            raise
    
    def auto_format_convert(self, image_data: bytes, target_type: str, preferred_format: str = None) -> Tuple[bytes, str]:
        """
        自动格式转换
        :param image_data: 原始图片数据
        :param target_type: 目标类型
        :param preferred_format: 偏好格式
        :return: (转换后的数据, 实际格式)
        """
        try:
            # 验证图片
            is_valid, error_msg, img = self.validate_image(image_data)
            if not is_valid:
                raise ValueError(error_msg)
            
            # 确定输出格式
            if preferred_format:
                output_format = preferred_format.upper()
            else:
                # 根据目标类型自动选择格式
                if target_type in ['favicon']:
                    output_format = 'ICO'
                elif target_type in ['apple_touch', 'pwa_192', 'pwa_512', 'logo']:
                    output_format = 'PNG'
                elif target_type in ['social']:
                    output_format = 'JPEG'
                else:
                    output_format = 'PNG'  # 默认PNG
            
            # 调整尺寸
            img = self.auto_resize(img, target_type)
            
            # 压缩转换
            converted_data = self.auto_compress(img, output_format, 'icon')
            
            logger.info(f"格式转换完成: {target_type} -> {output_format}, {len(converted_data)}字节")
            return converted_data, output_format
            
        except Exception as e:
            logger.error(f"格式转换失败: {str(e)}")
            raise
    
    def batch_process_icons(self, image_data: bytes, icon_types: List[str]) -> Dict[str, Dict[str, Union[bytes, str]]]:
        """
        批量处理图标
        :param image_data: 原始图片数据
        :param icon_types: 需要生成的图标类型列表
        :return: 类型->处理结果的字典
        """
        try:
            results = {}
            
            # 验证图片
            is_valid, error_msg, img = self.validate_image(image_data)
            if not is_valid:
                raise ValueError(error_msg)
            
            for icon_type in icon_types:
                try:
                    # 处理单个图标类型
                    converted_data, format_used = self.auto_format_convert(image_data, icon_type)
                    
                    results[icon_type] = {
                        'data': converted_data,
                        'format': format_used,
                        'size': len(converted_data),
                        'success': True
                    }
                    
                except Exception as e:
                    logger.error(f"处理图标类型{icon_type}失败: {str(e)}")
                    results[icon_type] = {
                        'error': str(e),
                        'success': False
                    }
            
            logger.info(f"批量处理完成，成功: {sum(1 for r in results.values() if r.get('success'))}/{len(icon_types)}")
            return results
            
        except Exception as e:
            logger.error(f"批量处理失败: {str(e)}")
            raise
    
    def get_image_info(self, image_data: bytes) -> Dict[str, Union[str, int, tuple]]:
        """
        获取图片信息
        :param image_data: 图片数据
        :return: 图片信息字典
        """
        try:
            is_valid, error_msg, img = self.validate_image(image_data)
            if not is_valid:
                return {'error': error_msg}
            
            return {
                'format': img.format,
                'mode': img.mode,
                'size': img.size,
                'width': img.size[0],
                'height': img.size[1],
                'file_size': len(image_data),
                'has_transparency': img.mode in ('RGBA', 'LA') or 'transparency' in img.info
            }
            
        except Exception as e:
            logger.error(f"获取图片信息失败: {str(e)}")
            return {'error': str(e)}

def create_image_processor() -> ImageProcessor:
    """创建图片处理器实例"""
    return ImageProcessor()

# 全局图片处理器实例
_processor = None

def get_image_processor() -> ImageProcessor:
    """获取全局图片处理器实例"""
    global _processor
    if _processor is None:
        _processor = create_image_processor()
    return _processor

# 便捷函数
def process_icon(image_data: bytes, icon_type: str, preferred_format: str = None) -> Tuple[bytes, str]:
    """
    处理单个图标的便捷函数
    :param image_data: 图片数据
    :param icon_type: 图标类型
    :param preferred_format: 偏好格式
    :return: (处理后的数据, 格式)
    """
    processor = get_image_processor()
    return processor.auto_format_convert(image_data, icon_type, preferred_format)

def batch_process(image_data: bytes, icon_types: List[str]) -> Dict[str, Dict[str, Union[bytes, str]]]:
    """
    批量处理的便捷函数
    :param image_data: 图片数据
    :param icon_types: 图标类型列表
    :return: 处理结果字典
    """
    processor = get_image_processor()
    return processor.batch_process_icons(image_data, icon_types)

def get_info(image_data: bytes) -> Dict[str, Union[str, int, tuple]]:
    """
    获取图片信息的便捷函数
    :param image_data: 图片数据
    :return: 图片信息
    """
    processor = get_image_processor()
    return processor.get_image_info(image_data) 