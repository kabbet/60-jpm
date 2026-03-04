package com.jpm.mqtt.auth;

import io.moquette.broker.security.IAuthorizatorPolicy;
import io.moquette.broker.subscriptions.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT 订阅/发布授权
 *
 * 科达规范：订阅通道必须以 /userdomains/{domain_id} 开头
 * 例如：/userdomains/w8oflp85/confs/+
 *       /userdomains/w8oflp85/confs/001112/#
 */
public class MqttAuthorizer implements IAuthorizatorPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(MqttAuthorizer.class);

    private final MqttAuthenticator authenticator;

    public MqttAuthorizer(MqttAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean canWrite(Topic topic, String user, String clientId) {
        // 终端客户端不允许向 broker 发布消息，只有服务端内部可以发布
        // 内部发布通过 broker.internalPublish() 直接调用，不走此方法
        LOG.warn("[MqttAuth] Publish rejected for client: clientId={}, topic={}", clientId, topic);
        return false;
    }

    @Override
    public boolean canRead(Topic topic, String user, String clientId) {
        String domainId = authenticator.getDomainId(clientId);

        if (domainId == null || domainId.isEmpty()) {
            LOG.warn("[MqttAuth] Subscribe rejected: no domainId found, clientId={}", clientId);
            return false;
        }

        // 通道必须以 /userdomains/{domain_id} 开头
        String topicStr      = topic.toString();
        String expectedPrefix = "/userdomains/" + domainId;

        if (!topicStr.startsWith(expectedPrefix)) {
            LOG.warn("[MqttAuth] Subscribe rejected: topic={} not in domain={}, clientId={}",
                    topicStr, domainId, clientId);
            return false;
        }

        LOG.info("[MqttAuth] Subscribe OK: topic={}, clientId={}", topicStr, clientId);
        return true;
    }
}
