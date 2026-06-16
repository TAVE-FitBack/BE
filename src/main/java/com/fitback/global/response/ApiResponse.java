package com.fitback.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"success", "code", "message", "data"})
public record ApiResponse<T>(

        @JsonProperty("success")
        boolean success,

        @JsonProperty("code")
        String code,

        @JsonProperty("message")
        String message,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("data")
        T data
) {
    public static <T> ApiResponse<T> onSuccess(T data) {
        return new ApiResponse<>(true, "SUCCESS", "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> ApiResponse<T> onSuccess(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data);
    }

    public static ApiResponse<Void> onFailure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> onFailure(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}