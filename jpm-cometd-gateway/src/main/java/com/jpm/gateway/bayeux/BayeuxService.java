package com.jpm.gateway.bayeux;

import com.jpm.gateway.auth.PlatformAuthClient;
import com.jpm.gateway.auth.PlatformAuthClient.AuthResult;
import com.jpm.gateway.config.GatewayConfig;
import org.cometd.bayeux.server.*;
import org.cometd.annotation.Session;
import org.cometd.annotation.Service;
import org.cometd.server.DefaultSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Service("BayeuxService")
public class BayeuxService {

    private static final Logger LOG = LoggerFactory.getLogger(BayeuxService.class);

    @Inject
    private BayeuxServer bayeuxServer;

    @Session
    private LocalSession sender;

    private final PlatformAuthClient authClient;

    public BayeuxService(PlatformAuthClient authClient) {
        this.authClient = authClient;
    }

    @PostConstruct
    public void init() {
        bayeuxServer.setSecurityPolicy((SecurityPolicy) new HandshakeSecurityPolicy(authClient));
        bayeuxServer.addListener(new SessionLifecycleListener());
        bayeuxServer.setOption("timeout", GatewayConfig.SESSION_TIMEOUT_SECONDS * 1000L);
        LOG.info("BayeuxService initialized");
    }

    // ── 握手安全策略 ─────────────────────────────
    private static class HandshakeSecurityPolicy extends DefaultSecurityPolicy {

        private final PlatformAuthClient authClient;

        HandshakeSecurityPolicy(PlatformAuthClient authClient) {
            this.authClient = authClient;
        }

        @Override
        public boolean canHandshake(BayeuxServer server,
                                     ServerSession session,
                                     ServerMessage message) {
            Map<String, Object> ext = message.getExt();
            String accountToken = ext != null ? (String) ext.get("account_token") : null;
            String ssoCookie    = (String) session.getAttribute("SSO_COOKIE_KEY");

            if (accountToken == null || ssoCookie == null) {
                LOG.warn("Handshake rejected: missing credentials, clientId={}", session.getId());
                return false;
            }

            AuthResult result = authClient.validate(accountToken, ssoCookie);
            if (!result.valid) {
                LOG.warn("Handshake rejected: auth failed, clientId={}", session.getId());
                return false;
            }

            // 存入 session 供后续订阅校验使用
            session.setAttribute("domain_id", result.domainId);
            session.setAttribute("username",  result.username);

            // 握手回复中写入 domain_id（终端需要用它来构造订阅通道名）
            Map<String, Object> replyExt = new HashMap<>();
            replyExt.put("user_domain_moid", result.domainId);
            replyExt.put("ack", true);
            message.getAssociated().getExt(true).putAll(replyExt);

            LOG.info("Handshake OK: username={}, domainId={}, clientId={}",
                    result.username, result.domainId, session.getId());
            return true;
        }

        @Override
        public boolean canSubscribe(BayeuxServer server,
                                     ServerSession session,
                                     ServerChannel channel,
                                     ServerMessage message) {
            String domainId  = (String) session.getAttribute("domain_id");
            String channelId = channel.getId();

            if (domainId == null) {
                LOG.warn("Subscribe rejected: no domain_id, clientId={}", session.getId());
                return false;
            }

            // 通道必须以 /userdomains/{domain_id} 开头
            if (!channelId.startsWith("/userdomains/" + domainId)) {
                LOG.warn("Subscribe rejected: channel={} not in domain={}", channelId, domainId);
                return false;
            }

            LOG.info("Subscribe OK: channel={}, clientId={}", channelId, session.getId());
            return true;
        }
    }

    // ── 会话生命周期日志 ─────────────────────────
    private static class SessionLifecycleListener implements BayeuxServer.SessionListener {

        @Override
        public void sessionAdded(ServerSession session, ServerMessage message) {
            LOG.info("Session connected: clientId={}", session.getId());
        }

        @Override
        public void sessionRemoved(ServerSession session, ServerMessage message, boolean timeout) {
            LOG.info("Session disconnected: clientId={}, username={}, timeout={}",
                    session.getId(), session.getAttribute("username"), timeout);
        }
    }
}
