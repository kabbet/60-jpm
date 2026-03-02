package com.jpm.gateway.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpm.common.model.ApiResponse;
import com.jpm.common.model.EventMessage;
import org.cometd.bayeux.Promise;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 接收 C++ 平台的业务事件，推送给订阅了对应通道的终端
 *
 * C++ 平台调用：
 *   POST /internal/event/publish
 *   Body: {"channel": "/userdomains/.../confs/.../mts/1", "method": "update"}
 */
public class EventPublishServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(EventPublishServlet.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BayeuxServer  bayeuxServer;
    private LocalSession  publisherSession;

    @Override
    public void init() throws ServletException {
        bayeuxServer = (BayeuxServer) getServletContext()
                .getAttribute(BayeuxServer.ATTRIBUTE);
        if (bayeuxServer == null) {
            throw new ServletException("BayeuxServer not found in ServletContext");
        }
        publisherSession = bayeuxServer.newLocalSession("cpp-platform-publisher");
        publisherSession.handshake();
        LOG.info("EventPublishServlet initialized");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        resp.setContentType("application/json");

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

        // 查找通道并推送
        ServerChannel channel = bayeuxServer.getChannel(event.getChannel());
        if (channel == null) {
            // 无订阅者，正常情况，不报错
            LOG.debug("No subscribers for channel: {}", event.getChannel());
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(MAPPER.writeValueAsString(ApiResponse.ok()));
            return;
        }

        // 推送数据格式：{"method": "update"} 或 {"method": "delete"}
        channel.publish(publisherSession, Map.of("method", event.getMethod()), Promise.noop());

        LOG.info("Published: channel={}, method={}", event.getChannel(), event.getMethod());
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(MAPPER.writeValueAsString(ApiResponse.ok()));
    }
}
