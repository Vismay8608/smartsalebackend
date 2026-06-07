package com.eauction.common.exception;

import com.eauction.common.response.ResponseCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public AppException(ResponseCode responseCode, HttpStatus httpStatus) {
        super(responseCode.getMessage());
        this.code = responseCode.getCode();
        this.httpStatus = httpStatus;
    }

    public AppException(ResponseCode responseCode, HttpStatus httpStatus, Throwable cause) {
        super(responseCode.getMessage(), cause);
        this.code = responseCode.getCode();
        this.httpStatus = httpStatus;
    }

    public AppException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    // ── Static factories ─────────────────────────────────────────────────────

    public static AppException unauthorized(ResponseCode code) {
        return new AppException(code, HttpStatus.UNAUTHORIZED);
    }

    public static AppException forbidden(ResponseCode code) {
        return new AppException(code, HttpStatus.FORBIDDEN);
    }

    public static AppException notFound(ResponseCode code) {
        return new AppException(code, HttpStatus.NOT_FOUND);
    }

    public static AppException conflict(ResponseCode code) {
        return new AppException(code, HttpStatus.CONFLICT);
    }

    public static AppException badRequest(ResponseCode code) {
        return new AppException(code, HttpStatus.BAD_REQUEST);
    }

    public static AppException internalError(ResponseCode code) {
        return new AppException(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static AppException internalError(ResponseCode code, Throwable cause) {
        return new AppException(code, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
