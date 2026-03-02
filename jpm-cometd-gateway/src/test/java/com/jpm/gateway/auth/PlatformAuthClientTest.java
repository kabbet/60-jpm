package com.jpm.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jpm.common.util.HttpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PlatformAuthClientTest {

    private HttpUtils           httpUtils;
    private PlatformAuthClient  authClient;
    private final ObjectMapper  mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        httpUtils  = Mockito.mock(HttpUtils.class);
        authClient = new PlatformAuthClient(httpUtils);
    }

    @Test
    void validate_success() {
        ObjectNode json = mapper.createObjectNode();
        json.put("valid",     true);
        json.put("domain_id", "domain-abc");
        json.put("username",  "admin");

        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(json);

        PlatformAuthClient.AuthResult result = authClient.validate("token-xxx", "cookie-xxx");

        assertTrue(result.valid);
        assertEquals("domain-abc", result.domainId);
        assertEquals("admin",      result.username);
    }

    @Test
    void validate_invalidCredentials() {
        ObjectNode json = mapper.createObjectNode();
        json.put("valid", false);

        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(json);

        PlatformAuthClient.AuthResult result = authClient.validate("bad-token", "bad-cookie");

        assertFalse(result.valid);
        assertNull(result.domainId);
    }

    @Test
    void validate_networkError() {
        when(httpUtils.get(anyString(), any(Map.class))).thenReturn(null);

        PlatformAuthClient.AuthResult result = authClient.validate("token-xxx", "cookie-xxx");

        assertFalse(result.valid);
    }
}
