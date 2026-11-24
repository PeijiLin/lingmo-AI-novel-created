package com.linpj.novel.create.exception;

/**
 * 抛出异常处理类
 */
public class ThrowsUtils {
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    public static void throwIf(boolean condition,ErrorCode errorCode) {
        throwIf(condition,new BusinessException(errorCode));
    }

    public static void throwIf(boolean condition,ErrorCode errorCode,String message) {
        throwIf(condition,new BusinessException(errorCode,message));
    }
}
