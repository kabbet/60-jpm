package com.jpm.mqtt.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.common.model.ApiResponse;
import com.jpm.common.model.EventMessage;
import io.moquette.broker.Server;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 接收 C++ 平台的业务事件，通过 Moquette 推送给订阅了对应通道的终端
 *
 * C++ 平台调用：
 *   POST /internal/mqtt/publish
 *   Body: {"channel": "/userdomains/{domain_id}/confs/{conf_id}/mts/1", "method": "update"}
 *
 * 注意：MQTT 通道格式与 CometD 相同，但通配符不同：
 *   MQTT: + 代表一级，# 代表多级
 */
public class MqttPublishServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(MqttPublishServlet.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Server mqttBroker;

    public MqttPublishServlet(Server mqttBroker) {
        this.mqttBroker = mqttBroker;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json;charset=UTF-8");

        // 解析请求体
        String body = req.getReader().lines().collect(Collectors.joining());
        EventMessage event;
        try {
            event = MAPPER.readValue(body, EventMessage.class);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(MAPPER.writeValueAsString(
                ApiResponse.fail(400, "Invalid JSON: " + e.getMessage())
            ));
            return;
        }

        if (!event.isValid()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(MAPPER.writeValueAsString(
                ApiResponse.fail(400, "Missing or invalid channel/method")
            ));
            return;
        }

        // 构造推送 payload：{"method": "update"} 或 {"method": "delete"}
        String payload = "{\"method\":\"" + event.getMethod() + "\"}";

        // 构造 MQTT PUBLISH 消息
        // topic 就是通道名（去掉开头的 /，MQTT topic 不以 / 开头）
        String topic = event.getChannel().startsWith("/")
                ? event.getChannel().substring(1)
                : event.getChannel();

        MqttPublishMessage message = MqttMessageBuilders.publish()
                .topicName(topic)
                .retained(false)
                .qos(MqttQoS.AT_MOST_ONCE)          // QoS 0，推送场景够用
                .payload(Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8))
                .build();

        // 通过 Moquette 内部推送，会自动匹配订阅了该 topic 的所有客户端
        mqttBroker.internalPublish(message, "jpm-platform-publisher");

        LOG.info("[MqttPublish] Published: topic={}, method={}", topic, event.getMethod());
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(MAPPER.writeValueAsString(ApiResponse.ok()));
    }
}
