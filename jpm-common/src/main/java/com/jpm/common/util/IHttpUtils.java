package com.jpm.common.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * HTTP 工具接口
 * 定义为接口方便单元测试 Mock，避免 Mockito 直接 instrument 具体类
 */
public interface IHttpUtils {

    /**
     * GET 请求，带 URL 参数
     */
    JsonNode get(String url, Map<String, String> params);

    /**
     * POST 请求，JSON body
     */
    JsonNode post(String url, Object body);
}