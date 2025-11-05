package com.ld.poetry.utils.storage;

import cn.hutool.core.io.FileUtil;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.utils.StringUtil;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "local.enable", havingValue = "true")
public class LocalUtil implements StoreService {

    @Value("${local.uploadUrl}")
    private String uploadUrl;

    @Value("${local.downloadUrl}")
    private String downloadUrl;

    @Autowired
    private ResourceService resourceService;

    @Override
    public void deleteFile(List<String> files) {
        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        for (String filePath : files) {
            File file = new File(filePath.replace(downloadUrl, uploadUrl));
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    log.info("文件删除成功：" + filePath);
                    resourceService.lambdaUpdate().eq(Resource::getPath, filePath).remove();
                } else {
                    log.error("文件删除失败：" + filePath);
                }
            } else {
                log.error("文件不存在或者不是一个文件：" + filePath);
            }
        }
    }

    @Override
    public FileVO saveFile(FileVO fileVO) {
        log.info("LocalUtil.saveFile 开始 - uploadUrl: {}, downloadUrl: {}", uploadUrl, downloadUrl);
        log.info("接收到的文件信息 - RelativePath: {}", fileVO.getRelativePath());
        
        if (!StringUtils.hasText(fileVO.getRelativePath()) ||
                fileVO.getRelativePath().startsWith("/") ||
                fileVO.getRelativePath().endsWith("/")) {
            throw new PoetryRuntimeException("文件路径不合法！");
        }

        String path = fileVO.getRelativePath();
        if (path.contains("/")) {
            String[] split = path.split("/");
            if (split.length > 5) {
                throw new PoetryRuntimeException("文件路径不合法！");
            }
            for (int i = 0; i < split.length - 1; i++) {
                if (!StringUtil.isValidDirectoryName(split[i])) {
                    throw new PoetryRuntimeException("文件路径不合法！");
                }
            }
            if (!StringUtil.isValidFileName(split[split.length - 1])) {
                throw new PoetryRuntimeException("文件路径不合法！");
            }
        }
        // 统一使用File.separator处理路径分隔符，确保Windows兼容
        String absolutePath = (uploadUrl + path).replace("/", File.separator);
        log.info("计算出的绝对路径: {}", absolutePath);
        if (FileUtil.exist(absolutePath)) {
            throw new PoetryRuntimeException("文件已存在！");
        }
        try {
            // 手动创建文件，确保更可靠
            File newFile = new File(absolutePath);
            File parentDir = newFile.getParentFile();
            log.info("父目录路径: {}", parentDir != null ? parentDir.getAbsolutePath() : "null");
            log.info("父目录是否存在: {}", parentDir != null && parentDir.exists());
            
            // 确保父目录存在
            if (parentDir != null) {
                if (parentDir.exists()) {
                    // 检查是否为目录
                    if (!parentDir.isDirectory()) {
                        log.warn("路径存在但不是目录，是一个文件！删除并重新创建: {}", parentDir.getAbsolutePath());
                        boolean deleted = parentDir.delete();
                        log.info("删除文件结果: {}", deleted);
                        if (deleted) {
                            boolean created = parentDir.mkdirs();
                            log.info("重新创建目录结果: {}", created);
                        } else {
                            throw new PoetryRuntimeException("无法删除同名文件: " + parentDir.getAbsolutePath());
                        }
                    } else {
                        log.info("父目录已存在且是目录");
                    }
                } else {
                    log.info("父目录不存在，开始创建: {}", parentDir.getAbsolutePath());
                    boolean created = parentDir.mkdirs();
                    log.info("创建父目录结果: {}, 目录是否存在: {}", created, parentDir.exists());
                    if (!created && !parentDir.exists()) {
                        throw new PoetryRuntimeException("创建父目录失败: " + parentDir.getAbsolutePath());
                    }
                }
            }
            
            // 创建文件
            log.info("准备创建文件: {}", newFile.getAbsolutePath());
            log.info("文件名: {}", newFile.getName());
            log.info("文件父目录: {}", newFile.getParent());
            
            if (!newFile.exists()) {
                try {
                    boolean fileCreated = newFile.createNewFile();
                    log.info("创建文件结果: {}", fileCreated);
                } catch (IOException e) {
                    log.error("创建文件失败，详细错误: {}", e.getMessage());
                    log.error("尝试的完整路径: {}", newFile.getAbsolutePath());
                    log.error("父目录是否真实存在: {}", newFile.getParentFile().exists());
                    log.error("父目录是否为目录: {}", newFile.getParentFile().isDirectory());
                    throw e;
                }
            } else {
                log.info("文件已存在，直接写入");
            }
            
            // 写入文件内容
            fileVO.getFile().transferTo(newFile);
            log.info("文件内容写入成功，文件大小: {} bytes", newFile.length());
            FileVO result = new FileVO();
            result.setAbsolutePath(absolutePath);
            result.setVisitPath(downloadUrl + path);
            log.info("LocalUtil.saveFile 完成 - VisitPath: {}", result.getVisitPath());
            return result;
        } catch (IOException e) {
            log.error("文件上传失败：", e);
            FileUtil.del(absolutePath);
            throw new PoetryRuntimeException("文件上传失败！");
        }
    }

    @Override
    public String getStoreName() {
        return StoreEnum.LOCAL.getCode();
    }
}
