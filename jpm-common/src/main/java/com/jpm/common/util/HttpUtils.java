package com.jpm.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 调用 C++ 平台 HTTP 接口的工具类
 * 各子模块共用，避免重复实现
 */
public class HttpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_MS = 3000;

    private final HttpClient httpClient;

    public HttpUtils(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * GET 请求，带 URL 参数
     */
    public JsonNode get(String url, Map<String, String> params) {
        try {
            var request = httpClient.newRequest(url);
            if (params != null) {
                params.forEach(request::param);
            }
            ContentResponse response = request
                    .timeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .send();

            if (response.getStatus() != 200) {
                LOG.warn("GET {} failed, status={}", url, response.getStatus());
                return null;
            }
            return MAPPER.readTree(response.getContentAsString());

        } catch (Exception e) {
            LOG.error("GET {} error: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * POST 请求，JSON body
     */
    public JsonNode post(String url, Object body) {
        try {
            String jsonBody = MAPPER.writeValueAsString(body);
            ContentResponse response = httpClient
                    .newRequest(url)
                    .method("POST")
                    .content(new StringContentProvider(jsonBody), "application/json")
                    .timeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .send();

            if (response.getStatus() != 200) {
                LOG.warn("POST {} failed, status={}", url, response.getStatus());
                return null;
            }
            return MAPPER.readTree(response.getContentAsString());

        } catch (Exception e) {
            LOG.error("POST {} error: {}", url, e.getMessage());
            return null;
        }
    }
}
