package com.resourcesharing.forum.common;

import java.time.Instant;

public record ApiResponse<T>(int code, String message, T data, String timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, currentTimestamp());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "created", data, currentTimestamp());
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, currentTimestamp());
    }

    private static String currentTimestamp() {
        return Instant.now().toString();
    }
}

