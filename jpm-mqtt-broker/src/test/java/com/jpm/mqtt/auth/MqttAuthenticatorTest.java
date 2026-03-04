package com.jpm.mqtt.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jpm.common.util.IHttpUtils;   // ✅ Mock 接口，不 Mock 具体类
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class MqttAuthenticatorTest {

    private IHttpUtils         httpUtils;    // ✅ 接口类型，Mockito 可以 Mock
    private MqttAuthenticator  authenticator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        httpUtils     = Mockito.mock(IHttpUtils.class);   // ✅ Mock 接口
        authenticator = new MqttAuthenticator(httpUtils);
    }

    @Test
    void checkValid_success() {
        ObjectNode json = mapper.createObjectNode();
        json.put("valid",     true);
        json.put("domain_id", "domain-abc");
        json.put("username",  "admin");
        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(json);

        boolean result = authenticator.checkValid(
                "client-1", "sso-cookie-xxx", "account-token-xxx".getBytes());

        assertTrue(result);
        assertEquals("domain-abc", authenticator.getDomainId("client-1"));
    }

    @Test
    void checkValid_invalidCredentials() {
        ObjectNode json = mapper.createObjectNode();
        json.put("valid",     false);
        json.put("error_msg", "Invalid session");
        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(json);

        boolean result = authenticator.checkValid(
                "client-2", "bad-cookie", "bad-token".getBytes());

        assertFalse(result);
        assertNull(authenticator.getDomainId("client-2"));
    }

    @Test
    void checkValid_networkError() {
        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(null);

        boolean result = authenticator.checkValid(
                "client-3", "cookie", "token".getBytes());

        assertFalse(result);
        assertNull(authenticator.getDomainId("client-3"));
    }

    @Test
    void onClientDisconnected_clearsDomainId() {
        ObjectNode json = mapper.createObjectNode();
        json.put("valid",     true);
        json.put("domain_id", "domain-xyz");
        json.put("username",  "user1");
        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(json);

        authenticator.checkValid("client-4", "cookie", "token".getBytes());
        assertNotNull(authenticator.getDomainId("client-4"));

        authenticator.onClientDisconnected("client-4");
        assertNull(authenticator.getDomainId("client-4"));
    }
}