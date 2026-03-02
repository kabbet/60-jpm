package com.jpm.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 推送事件消息
 *
 * C++ 平台 POST 到网关的请求体格式：
 * {
 *   "channel": "/userdomains/{domain_id}/confs/{conf_id}/cascades/0/mts/1",
 *   "method": "update"
 * }
 *
 * method 含义（对应科达规范）：
 *   update → 新建或数据更新，终端需再次 GET 获取详情
 *   delete → 资源删除，终端无需再发 GET 请求
 */
public class EventMessage {

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("method")
    private String method;

    public EventMessage() {}

    public EventMessage(String channel, String method) {
        this.channel = channel;
        this.method  = method;
    }

    public String getChannel() { return channel; }
    public String getMethod()  { return method; }

    public void setChannel(String channel) { this.channel = channel; }
    public void setMethod(String method)   { this.method = method; }

    public boolean isValid() {
        return channel != null && !channel.isEmpty()
            && method  != null && (method.equals("update") || method.equals("delete"));
    }
}
