package com.ld.poetry.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginCheck {
    int value() default 2;
    
    /**
     * 是否静默日志（不记录管理员请求日志）
     * 用于轮询接口等高频请求，避免日志刷屏
     */
    boolean silentLog() default false;
}
