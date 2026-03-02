package com.jpm.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.jpm.common.util.HttpUtils;
import com.jpm.gateway.config.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 调用 C++ 平台的内部鉴权接口
 * 握手时验证终端携带的 SSO_COOKIE_KEY 是否合法
 */
public class PlatformAuthClient {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAuthClient.class);

    private final HttpUtils httpUtils;

    public PlatformAuthClient(HttpUtils httpUtils) {
        this.httpUtils = httpUtils;
    }

    public static class AuthResult {
        public final boolean valid;
        public final String  domainId;
        public final String  username;

        public AuthResult(boolean valid, String domainId, String username) {
            this.valid    = valid;
            this.domainId = domainId;
            this.username = username;
        }

        public static AuthResult invalid() {
            return new AuthResult(false, null, null);
        }
    }

    /**
     * 验证 account_token + sso_cookie
     * 对应 C++ 接口：GET /internal/session/validate
     */
    public AuthResult validate(String accountToken, String ssoCookie) {
        JsonNode json = httpUtils.get(
            GatewayConfig.VALIDATE_SESSION_URL,
            Map.of("account_token", accountToken, "sso_cookie", ssoCookie)
        );

        if (json == null || !json.path("valid").asBoolean(false)) {
            LOG.warn("Auth failed for token={}", accountToken);
            return AuthResult.invalid();
        }

        String domainId = json.path("domain_id").asText("");
        String username = json.path("username").asText("");
        LOG.info("Auth success: username={}, domainId={}", username, domainId);
        return new AuthResult(true, domainId, username);
    }
}
