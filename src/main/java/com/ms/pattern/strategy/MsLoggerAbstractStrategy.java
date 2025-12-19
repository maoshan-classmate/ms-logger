package com.ms.pattern.strategy;

import com.ms.dto.Logger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.support.MultipartFilter;

import java.util.Arrays;
import java.util.List;

/**
 * @author xuzw
 * @date 2024/8/20 11:33
 * @version 1.0
 */
@Slf4j
public abstract class MsLoggerAbstractStrategy {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MsLoggerAbstractStrategy.class);

    /**
     * 过滤参数
     * @param args 参数数组
     * @return 过滤后的参数集合
     */
    protected List<Object> filterArgs(Object[] args) {
        return Arrays.stream(args).filter(object -> !(object instanceof MultipartFilter)
                && !(object instanceof HttpServletRequest)
                && !(object instanceof HttpServletResponse)
        ).toList();
    }
    /**
     * 日志输出
     * @param logger 日志对象
     * @param joinPoint 切点
     * @param outParams 返回参数
     * @param cost 花费时间
     */
    public abstract void doLog(Logger logger, JoinPoint joinPoint, String outParams, long cost);
}
