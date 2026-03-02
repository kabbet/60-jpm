package com.jpm.gateway.config;

/**
 * 网关配置
 * 生产部署时可改为从 resources/config.json 读取
 */
public class GatewayConfig {

    // 网关自身监听端口
    public static final int GATEWAY_PORT = 8081;

    // C++ 平台地址
    public static final String CPP_PLATFORM_URL = "http://127.0.0.1:8080";

    // C++ 内部鉴权接口
    public static final String VALIDATE_SESSION_URL =
            CPP_PLATFORM_URL + "/internal/session/validate";

    // CometD 挂载路径（对应科达规范 /api/v1/publish）
    public static final String COMETD_PATH = "/api/v1/publish";

    // 接收 C++ 平台推送事件的接口路径
    public static final String INTERNAL_PUBLISH_PATH = "/internal/event/publish";

    // Session 超时（秒）
    public static final int SESSION_TIMEOUT_SECONDS = 90;

    // 调用 C++ 鉴权接口的超时（毫秒）
    public static final int AUTH_TIMEOUT_MS = 3000;
}
