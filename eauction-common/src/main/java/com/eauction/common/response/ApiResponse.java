package com.eauction.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final Map<String, List<String>> errors;
    private final String traceId;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    // ─── Success ─────────────────────────────────────────────────────────────

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(build(true, ResponseCode.SUCCESS, null, data, null));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(build(true, ResponseCode.SUCCESS.getCode(), message, data, null));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(build(true, ResponseCode.CREATED, null, data, null));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(build(true, ResponseCode.CREATED.getCode(), message, data, null));
    }

    public static ResponseEntity<ApiResponse<Void>> noContent(String message) {
        return ResponseEntity.ok(build(true, ResponseCode.SUCCESS.getCode(), message, null, null));
    }

    // ─── Error ───────────────────────────────────────────────────────────────

    public static <T> ResponseEntity<ApiResponse<T>> error(
            HttpStatus status, ResponseCode code) {
        return ResponseEntity.status(status)
                .body(build(false, code.getCode(), code.getMessage(), null, null));
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(
            HttpStatus status, ResponseCode code, Map<String, List<String>> errors) {
        return ResponseEntity.status(status)
                .body(build(false, code.getCode(), code.getMessage(), null, errors));
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(build(false, code, message, null, null));
    }

    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(ResponseCode code) {
        return error(HttpStatus.UNAUTHORIZED, code);
    }

    public static <T> ResponseEntity<ApiResponse<T>> forbidden(ResponseCode code) {
        return error(HttpStatus.FORBIDDEN, code);
    }

    public static <T> ResponseEntity<ApiResponse<T>> notFound(ResponseCode code) {
        return error(HttpStatus.NOT_FOUND, code);
    }

    public static <T> ResponseEntity<ApiResponse<T>> conflict(ResponseCode code) {
        return error(HttpStatus.CONFLICT, code);
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(ResponseCode code) {
        return error(HttpStatus.BAD_REQUEST, code);
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(
            ResponseCode code, Map<String, List<String>> errors) {
        return error(HttpStatus.BAD_REQUEST, code, errors);
    }

    // ─── Internal builders ───────────────────────────────────────────────────

    private static <T> ApiResponse<T> build(
            boolean success, ResponseCode rc, String overrideMessage, T data, Map<String, List<String>> errors) {
        return build(success, rc.getCode(), overrideMessage != null ? overrideMessage : rc.getMessage(), data, errors);
    }

    private static <T> ApiResponse<T> build(
            boolean success, String code, String message, T data, Map<String, List<String>> errors) {
        return ApiResponse.<T>builder()
                .success(success)
                .code(code)
                .message(message)
                .data(data)
                .errors(errors)
                .build();
    }
}
