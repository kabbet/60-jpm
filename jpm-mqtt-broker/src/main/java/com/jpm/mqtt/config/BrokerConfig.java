package com.jpm.mqtt.config;

/**
 * MQTT Broker 配置
 * 生产部署时可改为从配置文件读取
 */
public class BrokerConfig {

    // MQTT Broker 监听端口（对应科达规范 1884）
    public static final int MQTT_PORT = 1884;

    // 内部 HTTP 服务端口（接收 C++ 平台的推送事件）
    public static final int HTTP_PORT = 8082;

    // C++ 平台地址
    public static final String CPP_PLATFORM_URL = "http://127.0.0.1:8080";

    // C++ 内部鉴权接口
    public static final String VALIDATE_SESSION_URL =
            CPP_PLATFORM_URL + "/internal/session/validate";

    // 接收 C++ 平台推送事件的路径
    public static final String INTERNAL_PUBLISH_PATH = "/internal/mqtt/publish";

    // 调用 C++ 鉴权接口超时（毫秒）
    public static final int AUTH_TIMEOUT_MS = 3000;

    // Moquette 数据持久化目录（Session、订阅关系）
    public static final String DATA_DIR = System.getProperty("java.io.tmpdir") + "/jpm-mqtt";
}
