package com.ms.aspect;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.json.JSONUtil;
import com.ms.event.MsLoggerEvent;
import com.ms.handler.MsLoggerHandler;
import com.ms.annotation.MsLogger;
import com.ms.config.MsLoggerProperties;
import com.ms.dto.Logger;
import com.ms.pattern.factory.MsLoggerFactory;
import com.ms.pattern.strategy.MsLoggerAbstractStrategy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;

/**
 * @author maoshan-classmate
 * @date 2024-08-14
 */
@Slf4j
@Component
@Aspect
public class MsLoggerAspect {

    private final MsLoggerProperties msLoggerProperties = MsLoggerProperties.getInstance();

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MsLoggerAspect.class);

    private static final TimeInterval TIMER = DateUtil.timer();

    private final ApplicationContext applicationContext;


    public MsLoggerAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }


    @Pointcut("@annotation(com.ms.annotation.MsLogger) || @within(com.ms.annotation.MsLogger) ")
    public void pointcut() {
    }


    @Around("pointcut()")
    public Object recordSysLogger(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger syslogger = new Logger();
        if (msLoggerProperties.isEnable()) {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null){
                HttpServletRequest request = requestAttributes.getRequest();
                syslogger.setIpAddress(JakartaServletUtil.getClientIP(request));
                syslogger.setApiUrl(request.getRequestURL().toString());
            }else{
                syslogger.setIpAddress("127.0.0.1");
            }
        }
        Class<? extends MsLoggerHandler> annotationHandler = getHandlerClass(joinPoint);
        MsLoggerHandler loggerHandler = null;
        if (annotationHandler != null && !annotationHandler.isInterface()) {
            loggerHandler = applicationContext.getBean(annotationHandler);
        }
        Object[] args = joinPoint.getArgs();
        Object result = null;
        if (msLoggerProperties.isEnable()) {
            Logger logger = buildSysLogger(joinPoint,syslogger);
            MsLoggerAbstractStrategy msLoggerStrategy = MsLoggerFactory.getMsLoggerStrategy(msLoggerProperties.getStrategy());
            try {
                TIMER.start();
                result = joinPoint.proceed(args);
                long cost = TIMER.intervalMs();
                logger.setCost(cost);
                msLoggerStrategy.doLog(logger, joinPoint, JSONUtil.toJsonStr(result), cost);
            } catch (Throwable e) {
                Class<? extends MsLoggerHandler> exceptionHandler = getExceptionHandler(joinPoint);
                MsLoggerHandler exceptionLoggerHandler = null;
                if (exceptionHandler != null && !exceptionHandler.isInterface()) {
                    exceptionLoggerHandler = applicationContext.getBean(exceptionHandler);
                }
                if (exceptionLoggerHandler != null) {
                    applicationContext.publishEvent(new MsLoggerEvent(exceptionLoggerHandler,joinPoint));
                }
                LOGGER.error("记录日志异常：{}", e.getMessage());
                throw e;
            }
        }
        if (loggerHandler != null) {
            applicationContext.publishEvent(new MsLoggerEvent(loggerHandler,joinPoint));
        }
        return result;
    }

    /**
     * 获取日志异常处理器
     * @param joinPoint  切点
     * @return 日志异常处理器
     */
    private Class<? extends MsLoggerHandler>  getExceptionHandler(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        MsLogger msLoggerAnnotation = method.getAnnotation(MsLogger.class);
        if (msLoggerAnnotation == null) {
            Class<?> declaringClass = method.getDeclaringClass();
            msLoggerAnnotation = declaringClass.getAnnotation(MsLogger.class);
        }else {
            if (msLoggerAnnotation.exceptionHandler().isInterface()){
                Class<?> declaringClass = method.getDeclaringClass();
                msLoggerAnnotation = declaringClass.getAnnotation(MsLogger.class);
            }
        }
        return msLoggerAnnotation.exceptionHandler();
    }


    /**
     * 获取日志增强处理器
     *
     * @param joinPoint 切点
     * @return 日志增强处理器
     */
    private Class<? extends MsLoggerHandler> getHandlerClass(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        MsLogger msLoggerAnnotation = method.getAnnotation(MsLogger.class);
        if (msLoggerAnnotation == null) {
            Class<?> declaringClass = method.getDeclaringClass();
            msLoggerAnnotation = declaringClass.getAnnotation(MsLogger.class);
        }else {
            if (msLoggerAnnotation.handler().isInterface()){
                Class<?> declaringClass = method.getDeclaringClass();
                msLoggerAnnotation = declaringClass.getAnnotation(MsLogger.class);
            }
        }
        return msLoggerAnnotation == null ? null : msLoggerAnnotation.handler();
    }

    /**
     * 构建日志对象
     *
     * @param joinPoint 切点
     * @param syslogger 日志对象
     * @return 日志对象
     */
    protected Logger buildSysLogger(ProceedingJoinPoint joinPoint, Logger syslogger) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        MsLogger logger = method.getAnnotation(MsLogger.class);
        Class<?> declaringClass = method.getDeclaringClass();
        syslogger.setMethodName(declaringClass.getSimpleName() + "." + method.getName());
        if (logger != null) {
            syslogger.setLogDesc(logger.desc());
        } else {
            MsLogger annotation = declaringClass.getAnnotation(MsLogger.class);
            if (annotation != null) {
                syslogger.setLogDesc(annotation.desc());
            }
        }
        try {
            // 处理入参
            Parameter[] parameters = methodSignature.getMethod().getParameters();
            HashMap<String, Object> paramMap = new HashMap<>();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < parameters.length; i++) {
                MsLogger auditLog = parameters[i].getAnnotation(MsLogger.class);
                if (auditLog != null) {
                    continue;
                }
                String name = parameters[i].getName();
                paramMap.put(name, args[i]);
            }
            syslogger.setParams(JSONUtil.toJsonStr(paramMap));
            return syslogger;
        } catch (Exception e) {
            LOGGER.error("构建入参异常：{}", e.getMessage());
        }
        return syslogger;
    }



}
