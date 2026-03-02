package com.jpm.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 统一 API 响应结构
 * 对应科达规范：{"success": 1, "error_code": 0, ...}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    @JsonProperty("success")
    private int success;

    @JsonProperty("error_code")
    private Integer errorCode;

    @JsonProperty("error_msg")
    private String errorMsg;

    // 业务数据，子类可扩展
    public static ApiResponse ok() {
        ApiResponse r = new ApiResponse();
        r.success = 1;
        r.errorCode = 0;
        return r;
    }

    public static ApiResponse fail(int errorCode, String errorMsg) {
        ApiResponse r = new ApiResponse();
        r.success = 0;
        r.errorCode = errorCode;
        r.errorMsg = errorMsg;
        return r;
    }

    public int getSuccess()       { return success; }
    public Integer getErrorCode() { return errorCode; }
    public String getErrorMsg()   { return errorMsg; }
}
