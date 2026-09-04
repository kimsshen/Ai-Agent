package com.example.industrialai.iiot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestControllerAdvice
public class IiotExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IiotExceptionHandler.class);

    @ExceptionHandler({InvalidDeviceIdException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(DeviceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "Device not found", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError(Exception exception, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.error("IIoT API request failed: traceId={}, method={}, uri={}",
                traceId, request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "IIoT API error",
                "The IIoT service could not complete the request", request, traceId);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String title, String detail,
                                           HttpServletRequest request) {
        return error(status, title, detail, request, resolveTraceId(request));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String title, String detail,
                                           HttpServletRequest request, String traceId) {
        return ResponseEntity.status(status).body(new ApiError(
                "urn:industrial-ai:error:" + status.value(), title, status.value(), detail,
                traceId, Instant.now()));
    }

    private String resolveTraceId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
