package com.jpm.gateway.bayeux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 在请求进入 CometD 之前，
 * 从 HTTP Cookie 中提取 SSO_COOKIE_KEY 存入 request attribute
 */
public class CookieExtractFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(CookieExtractFilter.class);
    private static final String SSO_COOKIE_NAME = "SSO_COOKIE_KEY";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            Cookie[] cookies = ((HttpServletRequest) request).getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (SSO_COOKIE_NAME.equals(cookie.getName())) {
                        request.setAttribute(SSO_COOKIE_NAME, cookie.getValue());
                        LOG.debug("SSO_COOKIE_KEY extracted from request");
                        break;
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Override public void init(FilterConfig filterConfig) {}
    @Override public void destroy() {}
}
