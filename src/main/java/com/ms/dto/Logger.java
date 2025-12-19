package com.ms.dto;


import lombok.Data;


/**
 * @author maoshan-classmate
 * @date 2024-08-14
 */
@Data
public class Logger {

    /**
     * 方法名称
     */
    private String methodName;

    /**
     * 日志描述
     */
    private String logDesc;

    /**
     * 方法入参
     */
    private String params;

    /**
     * 请求IP地址
     */
    private String ipAddress;

    /**
     * 请求耗时（毫秒）
     */
    private long cost;

    /**
     * 接口URL
     */
    private String apiUrl;

}
