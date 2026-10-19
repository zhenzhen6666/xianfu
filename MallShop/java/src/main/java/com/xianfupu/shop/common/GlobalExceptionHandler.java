package com.xianfupu.shop.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final com.xianfupu.shop.monitoring.MonitoringService monitoring;

    public GlobalExceptionHandler(com.xianfupu.shop.monitoring.MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(new ApiError(
                Instant.now(), ex.status().value(), ex.code(), ex.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "VALIDATION_FAILED", "请求参数校验失败", request.getRequestURI(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                Instant.now(), 400, "INVALID_JSON", "请求内容格式不正确", request.getRequestURI(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError(
                Instant.now(), 413, "INVALID_IMAGE", "图片不能超过 5MB", request.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request error: {}", request.getRequestURI(), ex);
        try {
            monitoring.error(request.getHeader("X-Request-Id"), "INTERNAL_ERROR", ex.getMessage(),
                    request.getMethod(), request.getRequestURI(), null, ex);
        } catch (Exception loggingFailure) {
            log.error("Unable to persist error monitoring record", loggingFailure);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                Instant.now(), 500, "INTERNAL_ERROR", "服务器暂时无法处理请求", request.getRequestURI(), null));
    }

    public record ApiError(Instant timestamp, int status, String code, String message,
                           String path, Map<String, String> fieldErrors) {
    }
}
