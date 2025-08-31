package com.ld.poetry.config;

import com.alibaba.fastjson.JSON;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.storage.FileFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class PoetryFilter extends OncePerRequestFilter {

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private FileFilter fileFilter;

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        if (!"OPTIONS".equals(httpServletRequest.getMethod())) {
            try {
                // 只记录真正的页面访问，不记录API和静态资源请求
                if (isPageVisit(httpServletRequest)) {
                    commonQuery.saveHistory(PoetryUtil.getIpAddr(httpServletRequest));
                }
            } catch (Exception e) {
                // 静默处理异常，不影响正常请求流程
            }

            if (fileFilter.doFilterFile(httpServletRequest, httpServletResponse)) {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpServletResponse.setContentType("application/json;charset=UTF-8");
                httpServletResponse.getWriter().write(JSON.toJSONString(com.ld.poetry.config.PoetryResult.fail(CodeMsg.PARAMETER_ERROR.getCode(), CodeMsg.PARAMETER_ERROR.getMsg())));
                return;
            }
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
    
    /**
     * 判断是否为页面访问（而非API或静态资源请求）
     * @param request HTTP请求
     * @return true表示是页面访问，需要记录访问历史
     */
    private boolean isPageVisit(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        
        // 排除API请求
        if (requestURI.startsWith("/api/")) {
            return false;
        }
        
        // 排除具体的API接口（这些是真正的API，不是页面）
        if (requestURI.startsWith("/imChatUserMessage") ||
            requestURI.startsWith("/imChatGroup") ||
            requestURI.startsWith("/imChatUserFriend") ||
            requestURI.startsWith("/imChatGroupUser") ||
            requestURI.startsWith("/webInfo") ||
            requestURI.startsWith("/sysConfig") ||
            requestURI.startsWith("/resource") ||
            requestURI.startsWith("/imageCompress")) {
            return false;
        }
        
        // 排除具体的API接口（区分API和页面路由）
        if (requestURI.matches("/user/[^/]+") ||  // /user/regist, /user/login 等API
            requestURI.matches("/article/[^/]+") ||  // /article/saveArticle 等API
            requestURI.matches("/weiYan/[^/]+") ||  // /weiYan/saveWeiYan 等API
            requestURI.matches("/treeHole/[^/]+") ||  // /treeHole/saveTreeHole 等API
            requestURI.matches("/comment/[^/]+") ||  // /comment/saveComment 等API
            requestURI.matches("/sort/[^/]+") ||  // /sort/saveSort 等API
            requestURI.matches("/label/[^/]+") ||  // /label/saveLabel 等API
            requestURI.matches("/admin/[^/]+")) {  // /admin/getAdminConfig 等API
            return false;
        }
        
        // 排除静态资源
        if (requestURI.startsWith("/static/") || 
            requestURI.startsWith("/css/") || 
            requestURI.startsWith("/js/") || 
            requestURI.startsWith("/images/") ||
            requestURI.startsWith("/favicon.ico")) {
            return false;
        }
        
        // 排除其他服务
        if (requestURI.startsWith("/python/")) {
            return false;
        }
        
        // 排除文件上传下载等
        if (requestURI.contains("/upload/") || 
            requestURI.contains("/download/") ||
            requestURI.endsWith(".jpg") ||
            requestURI.endsWith(".png") ||
            requestURI.endsWith(".gif") ||
            requestURI.endsWith(".ico") ||
            requestURI.endsWith(".css") ||
            requestURI.endsWith(".js") ||
            requestURI.endsWith(".map")) {
            return false;
        }
        
        // 其他请求视为页面访问
        return true;
    }
}
