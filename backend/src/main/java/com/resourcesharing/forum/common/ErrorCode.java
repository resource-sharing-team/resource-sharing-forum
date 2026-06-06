package com.resourcesharing.forum.common;

public enum ErrorCode {
    BAD_REQUEST(400, "请求参数错误"),
    SENSITIVE_CONTENT(40002, "内容含敏感词"),
    UNAUTHORIZED(401, "请先登录后再操作"),
    FORBIDDEN(403, "当前账号无操作权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
