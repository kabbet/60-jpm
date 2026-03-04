package com.jpm.mqtt;

import com.jpm.common.util.HttpUtils;
import com.jpm.mqtt.auth.MqttAuthenticator;
import com.jpm.mqtt.auth.MqttAuthorizer;
import com.jpm.mqtt.config.BrokerConfig;
import com.jpm.mqtt.publisher.MqttPublishServlet;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class MqttBrokerMain {

    private static final Logger LOG = LoggerFactory.getLogger(MqttBrokerMain.class);

    public static void main(String[] args) throws Exception {

        // ── 1. HTTP 客户端（调用 C++ 平台鉴权接口）──────
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        HttpUtils httpUtils = new HttpUtils(httpClient);

        // ── 2. 鉴权组件 ──────────────────────────────────
        MqttAuthenticator authenticator = new MqttAuthenticator(httpUtils);
        MqttAuthorizer    authorizer    = new MqttAuthorizer(authenticator);

        // ── 3. Moquette 配置 ─────────────────────────────
        IConfig brokerConfig = buildMoquetteConfig();

        // ── 4. 启动 Moquette MQTT Broker ─────────────────
        Server mqttBroker = new Server();
        mqttBroker.startServer(brokerConfig, null, null, authenticator, authorizer);

        LOG.info("[MqttBroker] MQTT Broker started on port {}", BrokerConfig.MQTT_PORT);

        // ── 5. 启动内部 HTTP 服务（接收 C++ 事件）────────
        org.eclipse.jetty.server.Server httpServer =
                new org.eclipse.jetty.server.Server();

        ServerConnector connector = new ServerConnector(httpServer);
        connector.setPort(BrokerConfig.HTTP_PORT);
        httpServer.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        httpServer.setHandler(context);

        // 注册事件推送 Servlet，传入 mqttBroker 引用
        context.addServlet(
            new ServletHolder(new MqttPublishServlet(mqttBroker)),
            BrokerConfig.INTERNAL_PUBLISH_PATH
        );

        httpServer.start();

        // ── 6. 注册 JVM 关闭钩子，优雅停机 ──────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("[MqttBroker] Shutting down...");
            try {
                httpServer.stop();
                mqttBroker.stopServer();
                httpClient.stop();
            } catch (Exception e) {
                LOG.error("[MqttBroker] Error during shutdown", e);
            }
        }));

        LOG.info("================================================");
        LOG.info("  MQTT Broker started on port {}",    BrokerConfig.MQTT_PORT);
        LOG.info("  HTTP publish  : http://0.0.0.0:{}{}", BrokerConfig.HTTP_PORT, BrokerConfig.INTERNAL_PUBLISH_PATH);
        LOG.info("  C++ platform  : {}",                BrokerConfig.CPP_PLATFORM_URL);
        LOG.info("================================================");

        httpServer.join();
    }

    /**
     * 构建 Moquette 内存配置
     * 无需 moquette.conf 文件，全部通过代码配置
     */
    private static IConfig buildMoquetteConfig() {
        Properties props = new Properties();

        // 监听端口
        props.setProperty(IConfig.PORT_PROPERTY_NAME,
                String.valueOf(BrokerConfig.MQTT_PORT));

        // 绑定地址
        props.setProperty(IConfig.HOST_PROPERTY_NAME, "0.0.0.0");

        // 数据持久化目录（Session、订阅关系断线重连用）
        props.setProperty(IConfig.DATA_PATH_PROPERTY_NAME, BrokerConfig.DATA_DIR);

        // 关闭匿名访问（必须提供 username/password）
        props.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "false");

        // 关闭 Moquette 内置的文件密码验证，使用我们自己的 IAuthenticator
        props.setProperty(IConfig.AUTHENTICATOR_CLASS_NAME, "");

        // 不启用 WebSocket（终端直接用 TCP MQTT，WebSocket 留给 CometD）
        props.setProperty(IConfig.WEB_SOCKET_PORT_PROPERTY_NAME, "disabled");

        // 关闭 Netty 统计（减少开销）
        props.setProperty("netty.mqtt.message_size", "65536");

        return new MemoryConfig(props);
    }
}
