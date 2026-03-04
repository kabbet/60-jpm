package com.jpm.mqtt.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.jpm.common.util.HttpUtils;
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

    private final HttpUtils httpUtils;

    // clientId → domain_id，鉴权通过后缓存，断开连接时清除
    private final ConcurrentHashMap<String, String> sessionDomainMap = new ConcurrentHashMap<>();

    public MqttAuthenticator(HttpUtils httpUtils) {
        this.httpUtils = httpUtils;
    }

    /**
     * Moquette 连接时回调
     *
     * @param clientId  MQTT 客户端 ID
     * @param username  sso_cookie
     * @param password  account_token（byte[]）
     */
    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        if (username == null || password == null || username.isEmpty()) {
            LOG_WARN(clientId, "missing username or password");
            return false;
        }

        String ssoCookie    = username;
        String accountToken = new String(password);

        // 调用 C++ 平台鉴权接口
        JsonNode result = httpUtils.get(
            BrokerConfig.VALIDATE_SESSION_URL,
            Map.of("account_token", accountToken, "sso_cookie", ssoCookie)
        );

        if (result == null || !result.path("valid").asBoolean(false)) {
            String reason = result != null ? result.path("error_msg").asText("auth failed") : "network error";
            LOG_WARN(clientId, reason);
            return false;
        }

        // 鉴权通过，缓存 domain_id 供订阅校验使用
        String domainId = result.path("domain_id").asText("");
        String userName  = result.path("username").asText("");
        sessionDomainMap.put(clientId, domainId);

        LOG.info("[MqttAuth] Auth OK: clientId={}, username={}, domainId={}", clientId, userName, domainId);
        return true;
    }

    /**
     * 获取已鉴权客户端的 domain_id
     */
    public String getDomainId(String clientId) {
        return sessionDomainMap.get(clientId);
    }

    /**
     * 客户端断开时清除缓存
     */
    public void onClientDisconnected(String clientId) {
        sessionDomainMap.remove(clientId);
        LOG.info("[MqttAuth] Session removed: clientId={}", clientId);
    }

    private void LOG_WARN(String clientId, String reason) {
        LOG.warn("[MqttAuth] Auth rejected: clientId={}, reason={}", clientId, reason);
    }
}
