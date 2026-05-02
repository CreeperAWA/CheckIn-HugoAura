package indi.etern.checkIn.controller.rest.advice;

import indi.etern.checkIn.throwable.action.ActionException;
import indi.etern.checkIn.throwable.auth.PermissionDeniedException;
import indi.etern.checkIn.throwable.exam.ExamException;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.UndeclaredThrowableException;

@RestControllerAdvice
public class RestExceptionHandler {
    final Logger logger = LoggerFactory.getLogger(RestExceptionHandler.class);
    
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorMessage handleException(Exception ex) {
        Throwable e = ex;
        if (ex instanceof UndeclaredThrowableException exception) {
            e = exception.getCause();
        }
        logger.error("Unhandled exception: {}: {}", e.getClass().getName(), e.getMessage(), e);
        return ErrorMessage.builder()
                .code("INTERNAL_ERROR")
                .message("服务器内部错误").build();
    }
    
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(PermissionDeniedException.class)
    public ErrorMessage handlePermissionDenied(PermissionDeniedException ex) {
        logger.warn("Permission denied: {}", ex.getMessage());
        return ErrorMessage.builder()
                .code("PERMISSION_DENIED")
                .message("权限不足").build();
    }
    
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({ActionException.class, ExamException.class})
    public ErrorMessage handleBadRequest(Exception ex) {
        logger.warn("Bad request: {}: {}", ex.getClass().getName(), ex.getMessage());
        return ErrorMessage.builder()
                .code("BAD_REQUEST")
                .message(ex.getMessage() != null ? ex.getMessage() : "请求参数错误").build();
    }
    
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(indi.etern.checkIn.throwable.auth.AuthException.class)
    public ErrorMessage handleAuthException(indi.etern.checkIn.throwable.auth.AuthException ex) {
        logger.warn("Auth exception: {}", ex.getMessage());
        return ErrorMessage.builder()
                .code("UNAUTHORIZED")
                .message("认证失败").build();
    }
    
    @Getter
    @Builder
    public static class ErrorMessage {
        private String code;
        private String message;
    }
}
