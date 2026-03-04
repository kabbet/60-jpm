package com.jpm.mqtt.auth;


import com.fasterxml.jackson.databind.JsonNode;
import com.jpm.common.util.IHttpUtils;
import com.jpm.mqtt.config.BrokerConfig;
import io.moquette.broker.security.IAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 连接鉴权
 *
 * 科达规范：
 *   username = sso_cookie（登录接口返回的 SSO_COOKIE_KEY）
 *   password = account_token（第一步鉴权返回的 token）
 *
 * 鉴权通过后将 domain_id 缓存到 sessionDomainMap，
 * 供后续订阅通道校验使用（通道必须以 /userdomains/{domain_id} 开头）
 */
public class MqttAuthenticator implements IAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(MqttAuthenticator.class);

    private final IHttpUtils httpUtils;  // ✅ 接口类型，Mockito 可以直接 Mock

    private final ConcurrentHashMap<String, String> sessionDomainMap = new ConcurrentHashMap<>();

    public MqttAuthenticator(IHttpUtils httpUtils) {
        this.httpUtils = httpUtils;
    }

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        if (username == null || password == null || username.isEmpty()) {
            LOG.warn("[MqttAuth] Rejected: missing credentials, clientId={}", clientId);
            return false;
        }

        String ssoCookie    = username;
        String accountToken = new String(password);

        JsonNode result = httpUtils.get(
                BrokerConfig.VALIDATE_SESSION_URL,
                Map.of("account_token", accountToken, "sso_cookie", ssoCookie)
        );

        if (result == null || !result.path("valid").asBoolean(false)) {
            String reason = result != null
                    ? result.path("error_msg").asText("auth failed")
                    : "network error";
            LOG.warn("[MqttAuth] Rejected: clientId={}, reason={}", clientId, reason);
            return false;
        }

        String domainId = result.path("domain_id").asText("");
        String userName  = result.path("username").asText("");
        sessionDomainMap.put(clientId, domainId);

        LOG.info("[MqttAuth] OK: clientId={}, username={}, domainId={}", clientId, userName, domainId);
        return true;
    }

    public String getDomainId(String clientId) {
        return sessionDomainMap.get(clientId);
    }

    public void onClientDisconnected(String clientId) {
        sessionDomainMap.remove(clientId);
        LOG.info("[MqttAuth] Session removed: clientId={}", clientId);
    }
}
