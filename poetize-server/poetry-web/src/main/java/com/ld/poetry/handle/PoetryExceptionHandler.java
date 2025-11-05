package com.ld.poetry.handle;

import com.alibaba.fastjson.JSON;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.utils.PoetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * <p>统一处理系统中抛出的各类异常，根据异常类型返回不同的响应结果。
 * 异常处理优先级从高到低依次为：登录异常 > 业务异常 > 参数校验异常 > 系统异常
 * 
 * @author sara (原作者)
 * @author LeapYa (优化者)
 */
@ControllerAdvice
@Slf4j
public class PoetryExceptionHandler {

    /**
     * 全局异常统一处理入口
     * 
     * @param ex 捕获的异常对象
     * @return 封装的错误响应结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public PoetryResult handleException(Exception ex) {
        String requestUrl = PoetryUtil.getRequest().getRequestURL().toString();
        
        // 登录异常：属于正常业务场景，仅记录警告日志
        if (ex instanceof PoetryLoginException) {
            log.warn("登录验证失败 - URL: {}, 原因: {}", requestUrl, ex.getMessage());
            return PoetryResult.fail(300, ex.getMessage());
        }

        // 记录异常详情供排查问题
        log.error("请求异常 - URL: {}", requestUrl);
        log.error("异常详情：", ex);
        
        // 业务运行时异常：返回业务错误信息
        if (ex instanceof PoetryRuntimeException) {
            return PoetryResult.fail(ex.getMessage());
        }

        // 参数校验异常：收集所有字段错误信息并返回
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validEx = (MethodArgumentNotValidException) ex;
            Map<String, String> fieldErrors = validEx.getFieldErrors()
                    .stream()
                    .collect(Collectors.toMap(
                            FieldError::getField, 
                            FieldError::getDefaultMessage
                    ));
            return PoetryResult.fail(JSON.toJSONString(fieldErrors));
        }

        // 缺少必填参数异常
        if (ex instanceof MissingServletRequestParameterException) {
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        // 未知异常：返回通用错误信息
        return PoetryResult.fail(CodeMsg.FAIL);
    }
}
