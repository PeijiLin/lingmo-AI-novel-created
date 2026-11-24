package com.linpj.novel.create.constant;

import com.linpj.novel.create.exception.ErrorCode;

/**
 * @author HL
 */
public class ResultUtils {
    public static <T> BaseResponse<T> success(T data, String msg) {
        return new BaseResponse<>(200,data,msg);
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(200,data,"OK");
    }
    public static <T> BaseResponse<T> success() {
        return new BaseResponse<>(200,null,"OK");
    }

    public static <T> BaseResponse<T> error(int code, String msg) {
        return new BaseResponse<>(code,null,msg);
    }

    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    public static <T> BaseResponse<T> error(ErrorCode errorCode, String msg) {
        return new BaseResponse<>(errorCode.getCode(),null,msg);
    }

}
